package AirBeam.Actor;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//import static AirBeam.Actor.Sandbox.SecurityMode.Extended;
//import static AirBeam.Actor.Sandbox.SecurityMode.Full;
//import static AirBeam.Actor.Sandbox.SecurityMode.Sandboxed;

public class AirDataArrayList extends ArrayList<Float> {
    private AccessControlContext privilegedContext;

    public AirDataArrayList(AccessControlContext acc){
        privilegedContext = acc;
    }
    private static int maxArraySize = 0;
    public boolean assertSize(int elementsRequired){
        maxArraySize = elementsRequired > maxArraySize ? elementsRequired : maxArraySize;
        return this.size() >= elementsRequired;
    }

    public List<Float> getSubList(int elements){
        maxArraySize = elements > maxArraySize ? elements : maxArraySize;
        return subList(this.size() > elements ? this.size() - elements : 0 , this.size());
    }

    public float getAverage(int elements){
        final List<Float> sList = getSubList( elements );
        return sList.stream().reduce(0f, (result,next) -> result + (next/(float)elements));
    }

    private List<Float> getModes(final List<Float> numbers) {
            final Map<Float, Long> countFrequencies =
                         numbers.stream()
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            final long maxFrequency =
                    countFrequencies.values().stream()
                        .mapToLong(count -> count)
                        .max().orElse(-1);
            return countFrequencies.entrySet().stream()
                    .filter(tuple -> tuple.getValue() == maxFrequency)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
    }

    public float getMinMode(int elements){
        maxArraySize = elements > maxArraySize ? elements : maxArraySize;
        List<Float> subList = getSubList( elements );
        List<Float> modes =  getModes(subList);
        if(modes.size() <= 0) return 0.0f;
        float res = modes.stream().reduce(Float.MAX_VALUE, (result,next) -> result < next ? result : next);
        return res;
    }

    public float getMaxMode(int elements){
        maxArraySize = elements > maxArraySize ? elements : maxArraySize;
        List<Float> subList = getSubList( elements );
        List<Float> modes =  getModes(subList);
        if(modes.size() <= 0) return 0.0f;
        float res = modes.stream().reduce(0.0f, (result,next) -> result > next ? result : next);
        return res;
    }

    public List<Float> subList(int from, int to){
        int elements = to - from;
        maxArraySize = elements > maxArraySize ? elements : maxArraySize;
        return super.subList(from, to);
    }

    private int getMaxSize(){
        return maxArraySize;
    }

    public boolean add(Float k){
        boolean r = super.add(k);
        if (size() > getMaxSize()){
            removeRange(0, size() - getMaxSize() - 1);
        }
        return r;
    }

    public Float getYoungest() {
        return get(size() - 1);
    }

    public Float getOldest() {
        return get(0);
    }
}
