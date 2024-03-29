# jskdata

A java downloader for [GeoNorge Download API](https://www.geonorge.no/for-utviklere/APIer-og-grensesnitt/nedlastingsapiet/) , 
[Hoydedata](http://www.hoydedata.no/Laserinnsyn) and OGC WFS.

## Maven

```
<repository>
    <id>ECC</id>
    <url>https://ecc-mvn.ams3.digitaloceanspaces.com/releases</url>
</repository>

<dependency>
    <groupId>no.jskdata</groupId>
    <artifactId>jskdata</artifactId>
    <version>1.0.20</version>
</dependency>
````

## Usage
```
  // download using GeoNorge "NedlastingsAPI" anonymously
  Downloader gndlapi = new GeoNorgeDownloadAPI();
  gndlapi.setFormatNameFilter(n -> n.contains("SOSI"));
  gndlapi.dataset("28c896d0-8a0d-4209-bf31-4931033b1082");
  gndlapi.download((fileName, in) -> { # or implement Receiver
  });
  
  // download using GeoNorge "NedlastingsAPI" with authentication.
  // NB: This requires a special user for HTTP Basic Authentication, not
  // a normal GeoID account.
  Downloader gndlapia = new GeoNorgeDownloadAPI(geonorgeUsername, geonorgePassword);
  gndlapia.setFormatNameFilter(n -> n.contains("SOSI"));
  gndlapia.dataset("6e05aefb-f90e-4c7d-9fb9-299574d0bbf6");
  gndlapia.download((fileName, in) -> { # or implement Receiver
  });

  // download using free and open National Elevation Models from hoydedata.no
  Downloader hoydedata = new Hoydedata();
  hoydedata.setUtmzone("33");
  hoydedata.dataset("DTM50");
  hoydedata.download((fileName,in) -> { # or implement Receiver
  });
  
```

## Test
```
mvn -Dgeonorge.username=... -Dgeonorge.password=... test
```

## Legal

This is just for examples on how to download from some public sites. It is important for every user to follow every instruction on every web page they download data. So please, make sure to have your agreements in place before downloading anything using this code or similar code.

## Thanks
* [@atlefren](https://github.com/atlefren/) for [skdata](https://github.com/atlefren/skdata) that jskdatas `KartverketDownload` was mostly a java variant of.
* [@jhy](https://github.com/jhy/) for [jsoup](https://github.com/jhy/jsoup) that jskdata uses for HTML parsing.
* [@bgrotan](https://github.com/bgrotan/) for adding support for [Hoydedata](http://www.hoydedata.no/Laserinnsyn).
* Google for Gson and Guava.
* Kartverket.

