# AirQualityActor
This repository to process the output from the AirbeamClient application. 
It acts as a rules engine and triggers external programs, depending on the configuration.

An example configuration is found within AirQualityActor.xml .

Simply put, it opens or closes the door depending upon the air quality 
as detected and reported by the AirBeam2 and forwarded by the AirbeamClient.

One typically calls this program on a linux command line, as follows:

java -jar AirbeamClient.jar | java -jar AirQualityActor.jar AirQualityActor.xml



