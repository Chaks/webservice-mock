# [WebService Mock](https://github.com/Chaks/webservice-mock)
**WebService Mock** is a very simple yet powerful tool for mocking WebServices.

## Why WebService Mock?

* Mocking is achieved in a non intrusive way, without writing even a single line of code, which means no need to mock the service stubs.
* Can be used for stress or load testing the applications without hitting the actual WebServices. Your service providers may not agree always.
* You want to simulate different test scenarios or conditions without engaging/involving your service providers.

## Getting started

1. Download the binary distribution from [http://webservice-mock.googlecode.com/files/webservice-mock-1.0-BETA-bin.zip](http://webservice-mock.googlecode.com/files/webservice-mock-1.0-BETA-bin.zip), which contains the main jar and the required libraries.
2. Download the json configuration from [http://webservice-mock.googlecode.com/files/services.json](http://webservice-mock.googlecode.com/files/services.json) and configure the service endpoint. Refer to the below section on how to configure.
3. Extract the zip from step 1 and run the below. **I promise, you will definitely love this little tool.**
```
java -jar webservice-mock-1.0-BETA.jar 2345 / /home/dchakr/testDir /home/dchakr/testDir/services.json
```

## How to configure?

```
[
  >{ "hostName": "www.webservicex.net","port": 80,"contextPath": "/globalweather.asmx", "refreshInterval": 3,"mirrorEnabled": >false,"detachHeader":true,"detachElementList":["Envelope/Body/GetCitiesByCountry/CountryName"],"ignoreOperationList":["GetWeather"],"directoryBrowsing":true},
  >{ "hostName": "graphical.weather.gov","port": 80,"contextPath": "/xml/SOAP_server/ndfdXMLserver.php", "refreshInterval": 5,"mirrorEnabled": >true,"detachHeader":true,"detachElementList":[""],"ignoreOperationList":[""],"directoryBrowsing":true}
]
```


Extract the zip and run the following co

## How it works?

## Authors

**Darimireddi Chakravarthi**

+ [https://twitter.com/darimireddy](https://twitter.com/darimireddy)
+ [https://github.com/Chaks](https://github.com/Chaks)
