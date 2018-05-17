package AirBeam.Actor;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.lang.reflect.ReflectPermission;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import javax.tools.SimpleJavaFileObject;
import javax.tools.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

//import static AirBeam.Actor.Sandbox.SecurityMode.Full;
//import static AirBeam.Actor.Sandbox.SecurityMode.Sandboxed;

public class AirQualityActor {
    public static class JavaSourceFromString extends SimpleJavaFileObject {
        final String code;

        public JavaSourceFromString( String name, String code) {
            super( URI.create("string:///" + name.replace('.','/') + Kind.SOURCE.extension),Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
    private static class StringJavaFileObject extends SimpleJavaFileObject {
        private final String code;

        public StringJavaFileObject(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return code;
        }
    }

    private static class ClassJavaFileObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream;
        private final String className;

        protected ClassJavaFileObject(String className, Kind kind) {
            super(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind);
            this.className = className;
            outputStream = new ByteArrayOutputStream();
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return outputStream;
        }

        public byte[] getBytes() {
            return outputStream.toByteArray();
        }

        public String getClassName() {
            return className;
        }
    }

    private static class SimpleJavaFileManager extends ForwardingJavaFileManager {
        private final List<ClassJavaFileObject> outputFiles;

        protected SimpleJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
            outputFiles = new ArrayList<ClassJavaFileObject>();
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            ClassJavaFileObject file = new ClassJavaFileObject(className, kind);
            outputFiles.add(file);
            return file;
        }

        public List<ClassJavaFileObject> getGeneratedOutputFiles() {
            return outputFiles;
        }
    }

    final public static class CompiledClassLoader extends ClassLoader {
        private final List<ClassJavaFileObject> files;

        private CompiledClassLoader(List<ClassJavaFileObject> files) {
            this.files = files;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Iterator<ClassJavaFileObject> itr = files.iterator();
            while (itr.hasNext()) {
                ClassJavaFileObject file = itr.next();
                if (file.getClassName().equals(name)) {
                    itr.remove();
                    byte[] bytes = file.getBytes();
                    return super.defineClass(name, bytes, 0, bytes.length);
                }
            }
            return super.findClass(name);
        }
    }

    static class AirDataParser extends DefaultHandler {
        static String currentNodeName = "";
        static StringBuilder currentNodeValue = new StringBuilder();
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            currentNodeName = qName;
            currentNodeValue.setLength(0);
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            super.endElement(uri, localName, qName);
            if(qName.equals(currentNodeName)){
                float currentValue = Float.valueOf(currentNodeValue.toString());
                currentNodeValue.setLength(0);
                switch(qName){
                    case "AirBeam2-F":  actionDefinitions.getFirst().condition.setF(currentValue); break;
                    case "AirBeam2-RH": actionDefinitions.getFirst().condition.setRH(currentValue); break;
                    case "AirBeam2-PM1": actionDefinitions.getFirst().condition.setPM1(currentValue); break;
                    case "AirBeam2-PM2.5": actionDefinitions.getFirst().condition.setPM2_5(currentValue); break;
                    case "AirBeam2-PM10": actionDefinitions.getFirst().condition.setPM10(currentValue);
                        handleEvents(); break;
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
             super.characters(ch, start, length);
             currentNodeValue.append(ch, start, length);
        }
    }
    public static class ActionDefinition{
        public String id;
        public String actionType;
        public String action;
        public String conditionString;
        public IAirBeamActionPredicate condition;

        private ActionDefinition(){}
        public ActionDefinition(String id, String actionType, String action, String conditionString, IAirBeamActionPredicate condition){
            this.id = id;
            this.actionType = actionType;
            this.action = action;
            this.condition = condition;
            this.conditionString = conditionString;
        }
    }

    private static String currentState = "";
    private static long lastChanged = 0;
    private static int doorChangeTimeout = 10;
    private static int warmUpIgnore = 10;
    private static long startUpTime = System.currentTimeMillis();

    static LinkedList<ActionDefinition> actionDefinitions = new LinkedList<ActionDefinition>();

    static AtomicInteger classIndexer  = new AtomicInteger();

    public static void executeCommand(String command){
        counter=0;
        System.out.println();
        System.out.println("Executing command : " + command);
        System.out.println();
        try {
            final Runtime rt = Runtime.getRuntime();
            rt.exec(command);
        }
        catch(IOException ex){
            ex.printStackTrace(System.err);
        }
    }

    private static int counter = 0;
    public static void handleEvents() {
        //if(counter++ %80 == 0) System.out.println();
        System.out.flush();

        //try {Thread.sleep(1000);} catch(Exception ex){}
        if(System.currentTimeMillis() > startUpTime + (warmUpIgnore * 1000))
        {
            for (ActionDefinition actionDefinition : actionDefinitions) {
                System.out.printf("%s : Condition: %s %s :%s / %s (buffersize:%d)",
                        new SimpleDateFormat("MM/dd/yyyy hh:mm:ss").format(new Date()),
                        (actionDefinition.condition.areConditionsMet()?"True ":"False"),
                        actionDefinition.id,
                        actionDefinition.conditionString
                                .replace("pm1List.getAverage(60)", "pm1List.getAverage(60)[" + actionDefinition.condition.getPM1List().getAverage(60) + "]")
                                .replace("pm2_5List.getAverage(60)", "pm2_5List.getAverage(60)[" + actionDefinition.condition.getPM2_5List().getAverage(60) + "]")
                                .replace("pm10List.getAverage(60)", "pm10List.getAverage(60)[" + actionDefinition.condition.getPM10List().getAverage(60) + "]")
                                .replace("pm1List.getMinMode(3600)", "pm1List.getMinMode(3600)[" + actionDefinition.condition.getPM1List().getMinMode(3600) + "]")
                                .replace("pm2_5List.getMinMode(3600)", "pm2_5List.getMinMode(3600)[" + actionDefinition.condition.getPM2_5List().getMinMode(3600) + "]")
                                .replace("pm10List.getMinMode(3600)", "pm10List.getMinMode(3600)[" + actionDefinition.condition.getPM10List().getMinMode(3600) + "]")
                                .replace("pm1List.getMaxMode(600)", "pm1List.getMaxMode(600)[" + actionDefinition.condition.getPM1List().getMaxMode(600) + "]")
                                .replace("pm2_5List.getMaxMode(600)", "pm2_5List.getMaxMode(600)[" + actionDefinition.condition.getPM2_5List().getMaxMode(600) + "]")
                                .replace("pm10List.getMaxMode(600)", "pm10List.getMaxMode(600)[" + actionDefinition.condition.getPM10List().getMaxMode(600) + "]")
                                .replace("pm1 ", "pm1[" + actionDefinition.condition.getPM1() + "] ")
                                .replace("pm2_5 ", "pm2_5[" + actionDefinition.condition.getPM2_5() + "] ")
                                .replace("pm10 ", "pm10[" + actionDefinition.condition.getPM10() + "] ")
                        ,
                        actionDefinition.actionType,
                        actionDefinition.condition.getPM1List().size()
                        );
                System.out.println();
                if (actionDefinition.condition.areConditionsMet()) {
                    if (!(actionDefinition.actionType.toLowerCase().equals(currentState))) {
                        if(System.currentTimeMillis() > lastChanged + (doorChangeTimeout * 1000)) {
                            final String action = actionDefinition.action;
                            AccessController.doPrivileged((PrivilegedAction) () -> {executeCommand(action);return null;}, globalControlContext);
                            currentState = actionDefinition.actionType.toLowerCase();
                            lastChanged = System.currentTimeMillis();
                        }
                    }
                }
            }

            actionDefinitions.getFirst().condition.updateLists();
        }
    }

    static void loadConfigFile(String configPath) throws Exception{
    /*
        config file will be in the format

       <settings>
         <DoorChangeTimeoutInSeconds>10</DoorChangeTimeoutInSeconds>
         <ActionCondition id='averagePM1ExceededBy100PercentOverLast5Seconds' type= 'close' action='cmd /c closeDoor.bat' ><![CDATA[ pm1 + pm1 >= pm1List.getAverage(5)) ]]></ActionCondition>
       </settings>
    */

        List<String> conditions = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<String> actionTypes = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        String path = configPath;

        XPath xpath = XPathFactory.newInstance().newXPath();
        String xpathExpression = "/settings";
        InputSource inputSource = new InputSource(path);

        try {
            NodeList lstRoot = (NodeList) xpath.compile(xpathExpression).evaluate(inputSource, XPathConstants.NODESET);
            NodeList lstChilds = lstRoot.item(0).getChildNodes();

            for (int i = 0; i < lstChilds.getLength(); i++) {

                if(lstChilds.item(i).getNodeName().equals("DoorChangeTimeoutInSeconds")) {
                    String value = lstChilds.item(i).getTextContent();
                    doorChangeTimeout = Integer.valueOf(value);
                }
                else if(lstChilds.item(i).getNodeName().equals("WarmUpIgnoreInSeconds")) {
                    String value = lstChilds.item(i).getTextContent();
                    warmUpIgnore = Integer.valueOf(value);
                }

                else if(lstChilds.item(i).getNodeName().equals("ActionCondition")) {
                    NamedNodeMap map = lstChilds.item(i).getAttributes();
                    Node idNode = map.getNamedItem("id");
                    if(idNode == null ){
                        System.err.println("ActionCondition encountered without id attribute - ignored");
                        continue;
                    }
                    Node actionTypeNode = map.getNamedItem("type");
                    if(actionTypeNode == null ){
                        System.err.println("ActionCondition encountered without action type attribute - ignored");
                        continue;
                    }
                    if(! (actionTypeNode.getNodeValue().toLowerCase().equals("open") || actionTypeNode.getNodeValue().toLowerCase().equals("close") )){
                        System.err.println("ActionCondition encountered without correct action type attribute:" + actionTypeNode.getNodeValue() + " - ignored");
                        continue;
                    }

                    Node actionNode = map.getNamedItem("action");
                    if(actionNode == null ){
                        System.err.println("ActionCondition encountered without action attribute - ignored");
                        continue;
                    }

                    String conditionString = lstChilds.item(i).getTextContent();
                    if(conditionString == null ){
                        System.err.println("ActionCondition encountered without condition - ignored");
                        continue;
                    }

                    actionTypes.add(actionTypeNode.getNodeValue());
                    actions.add(actionNode.getNodeValue());
                    conditions.add(conditionString);
                    ids.add(idNode.getNodeValue());
                }
            }

            List<IAirBeamActionPredicate> predicates = getPredicates(conditions);

            Iterator<String> actionTypeIterator = actionTypes.listIterator();
            Iterator<String> actionIterator = actions.listIterator();
            Iterator<String> conditionIterator = conditions.listIterator();
            Iterator<String> idIterator = ids.listIterator();
            predicates.forEach(predicate ->
            {
                String id = idIterator.next();
                String actionType = actionTypeIterator.next();
                String action = actionIterator.next();
                String condition = conditionIterator.next();
                actionDefinitions.add(new ActionDefinition(id, actionType, action, condition, predicate));
            });

        } catch (XPathExpressionException e) {
            e.printStackTrace(System.err);
        }
    }

    private static SAXParserFactory saxParserFactory;
    private static SAXParser saxParser;
    private static AirDataParser parseEventsHandler;
    static
    {
        saxParserFactory = SAXParserFactory.newInstance();

        try {
            saxParser = saxParserFactory.newSAXParser();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
        parseEventsHandler=new AirDataParser();
    }

    static void processInput() throws SAXException, ParserConfigurationException, IOException, InterruptedException {
        /*
            The input on System.in will/should be in the format:

             some rubbish here
            <AirBeam.Actor.AirBeamActionPredicate-F>78.0</AirBeam.Actor.AirBeamActionPredicate-F>
            <AirBeam.Actor.AirBeamActionPredicate-RH>62.0</AirBeam.Actor.AirBeamActionPredicate-RH>
            <AirBeam.Actor.AirBeamActionPredicate-PM1>1.0</AirBeam.Actor.AirBeamActionPredicate-PM1>
            <AirBeam.Actor.AirBeamActionPredicate-PM2.5>3.0</AirBeam.Actor.AirBeamActionPredicate-PM2.5>
            <AirBeam.Actor.AirBeamActionPredicate-PM10>5.0</AirBeam.Actor.AirBeamActionPredicate-PM10>
         */
        byte [] heading = "<Document>".getBytes("UTF-8");
        PushbackInputStream pushbackInputStream = new PushbackInputStream(System.in, heading.length);
        pushbackInputStream.unread(heading, 0, heading.length);

        saxParser.parse(pushbackInputStream, parseEventsHandler);
    }

    public static List<IAirBeamActionPredicate> getPredicates(List<String> condition){
        List<IAirBeamActionPredicate> predicates = new ArrayList<>();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if( compiler == null) throw new RuntimeException( "Compiler unavailable - please ensure that jou are using the Java SDK rather than the JRE\nIt may help to specify the path to the java executable.");

        List<String> options = new ArrayList<String>();
        options.add( "-classpath");
        URLClassLoader urlClassLoader = (URLClassLoader)Thread.currentThread().getContextClassLoader();

        List<JavaSourceFromString> fileObjects = new ArrayList<>();

        List<String> classNames = new ArrayList<>();

        condition.forEach( el -> {
            String className = "AirBeamActionPredicate_" + classIndexer.incrementAndGet();
            classNames.add(className);
            StringBuilder sbuilder = new StringBuilder();
            sbuilder.append("package AirBeam.Actor; ");


            sbuilder.append("public class ");
            sbuilder.append(className);
            sbuilder.append(" extends AirBeam.Actor.AirBeamActionPredicate { public boolean areConditionsMet() { return " + el + "; }}");
            fileObjects.add(new JavaSourceFromString( className, sbuilder.toString()));
        });


        StringBuilder sb = new StringBuilder();
        for (URL url : urlClassLoader.getURLs()) {
            sb.append(url.getFile()).append(File.pathSeparator);
        }
        options.add(sb.toString());

        SimpleJavaFileManager fileManager = new SimpleJavaFileManager(compiler.getStandardFileManager(null, null, null));

        StringWriter output = new StringWriter();
        boolean success;
        success = compiler.getTask(output, fileManager, null, options, null, fileObjects).call();

        if( ! success) {
            throw new RuntimeException("Compilation of configuration file failed :" + output);
        }

        CompiledClassLoader classLoader;
        classLoader = new CompiledClassLoader(fileManager.getGeneratedOutputFiles());

        classNames.forEach( className -> {
            try {
                final Class<?> predicateClass = classLoader.loadClass("AirBeam.Actor." + className);

                IAirBeamActionPredicate predicate;
                predicate = (IAirBeamActionPredicate) AccessController.doPrivileged((PrivilegedAction) () -> {
                        try {
                            return predicateClass.newInstance();
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }
                    }, globalControlContext);
                predicates.add(predicate);
            }
            catch(Exception ex1){ex1.printStackTrace(System.err);}
        });

        IAirBeamActionPredicate first = predicates.listIterator().next();
        first.setFList( new AirDataArrayList(globalControlContext) );
        first.setRHList( new AirDataArrayList(globalControlContext) );
        first.setPM1List(new AirDataArrayList(globalControlContext) );
        first.setPM2_5List(new AirDataArrayList(globalControlContext) );
        first.setPM10List(new AirDataArrayList(globalControlContext) );

  //      predicates.forEach(el -> Sandbox.confine(el.getClass(), new Permissions()));

        return predicates;
    }

    private static AccessControlContext globalControlContext;
    public static void main(String [] args){
        globalControlContext = AccessController.getContext();
/*
        final Permissions permissions = new Permissions();
        permissions.add(new PropertyPermission("jaxp.debug", "read"));
        permissions.add(new PropertyPermission("nonBatchMode", "read"));
        permissions.add(new ReflectPermission("suppressAccessChecks"));
        permissions.add(new RuntimePermission("getProtectionDomain"));
        //globalControlContext = new AccessControlContext(new ProtectionDomain[]{new ProtectionDomain(null, permissions, permissions.getClass().getClassLoader(),null )});
*/
        try {
            //FileInputStream is = new FileInputStream(new File("c:/temp/aircasting.txt"));
            //System.setIn(is);
            if(args.length == 0 || ! new File(args[0]).exists()){
                System.err.println("Usage\n\n" +
                                   "java -jar AirQualityActor.jar /path/to/config/file.xml \n");
            }

            loadConfigFile(args[0]);
            Policy.setPolicy(new SandboxSecurityPolicy());
            System.setSecurityManager(new SecurityManager());
            processInput();
        }
        catch(Throwable t){
            t.printStackTrace(System.err);
            System.exit(-1);
        }
    }

}
