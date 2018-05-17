package AirBeam.Actor;

import java.util.List;

public interface IAirBeamActionPredicate {
    boolean areConditionsMet();

    void setF(float value);
    void setRH(float value);
    void setPM1(float value);
    void setPM2_5(float value);
    void setPM10(float value);

    void setFList(AirDataArrayList value);
    void setRHList(AirDataArrayList value);
    void setPM1List(AirDataArrayList value);
    void setPM2_5List(AirDataArrayList value);
    void setPM10List(AirDataArrayList value);

    float getF();
    float getRH();
    float getPM1();
    float getPM2_5();
    float getPM10();

    AirDataArrayList getFList();
    AirDataArrayList getRHList();
    AirDataArrayList getPM1List();
    AirDataArrayList getPM2_5List();
    AirDataArrayList getPM10List();

    void updateLists();

}
