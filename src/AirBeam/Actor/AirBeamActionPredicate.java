package AirBeam.Actor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AirBeamActionPredicate implements IAirBeamActionPredicate {

    public static AirDataArrayList fList     = null;
    public static AirDataArrayList rhList    = null;
    public static AirDataArrayList pm1List   = null;
    public static AirDataArrayList pm2_5List = null;
    public static AirDataArrayList pm10List  = null;

    public static float f = 0f;      // C = (F- 32) * 5 / 9
    public static float rh = 0f;
    public static float pm1 = 0f;
    public static float pm2_5 = 0f;
    public static float pm10 = 0f;

    private static void examples(){
        boolean averagePM2_5ExceededBy100Percent =  pm2_5 + pm2_5  >= pm2_5List.subList(pm2_5List.size() - 10, pm2_5List.size()).stream().reduce(0f, (result,next) -> result + (next/10f)); // check if current particle matter level exceeds the average particle matter level recorded over the last 10 seconds
        boolean averageTemperatureExceeded = f > fList.getSubList(600).stream().reduce(0f, (result,next) -> result + (next/600f)); // check if current temperature exceeds the average temperature over the last 10 minutes
        boolean averagePM1ExceededBy10Percent =  pm1 * 1.1f > pm1List.getAverage(5); // check pm1 exceeds the average pm1 over the last 5 seconds worth
    }

    public abstract boolean areConditionsMet(int alreadyValidFor);

    @Override
    public void setF(float value) {
        f = value;
    }

    @Override
    public void setRH(float value) {
        rh = value;
    }

    @Override
    public void setPM1(float value) {
        pm1 = value;
    }

    @Override
    public void setPM2_5(float value) {
        pm2_5 = value;
    }

    @Override
    public void setPM10(float value) {
        pm10 = value;
    }

    @Override
    public void setFList(AirDataArrayList value) {
        if(fList == null) fList = value;
    }

    @Override
    public void setRHList(AirDataArrayList value) {
        if(rhList == null) rhList = value;
    }

    @Override
    public void setPM1List(AirDataArrayList value) {
        if(pm1List == null) pm1List = value;
    }

    @Override
    public void setPM2_5List(AirDataArrayList value) {
        if(pm2_5List == null) pm2_5List = value;
    }

    @Override
    public void setPM10List(AirDataArrayList value) {
        if(pm10List == null) pm10List = value;
    }

    @Override
    public float getF() {
        return f;
    }

    @Override
    public float getRH() {
        return rh;
    }

    @Override
    public float getPM1() {
        return pm1;
    }

    @Override
    public float getPM2_5() {
        return pm2_5;
    }

    @Override
    public float getPM10() {
        return pm10;
    }

    @Override
    public AirDataArrayList getFList() {
        return fList;
    }

    @Override
    public AirDataArrayList getRHList() {
        return rhList;
    }

    @Override
    public AirDataArrayList getPM1List() {
        return pm1List;
    }

    @Override
    public AirDataArrayList getPM2_5List() {
        return pm2_5List;
    }

    @Override
    public AirDataArrayList getPM10List() {
        return pm10List;
    }

    @Override
    public void updateLists() {
        fList.add(f);
        rhList.add(rh);
        pm1List.add(pm1);
        pm2_5List.add(pm2_5);
        pm10List.add(pm10);
    }
}

