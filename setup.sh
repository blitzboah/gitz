#!/bin/bash

javac GitRepository.java Main.java
jar cfm gitz.jar MANIFEST.MF GitRepository.class Main.class
sudo mv gitz.jar /usr/local/bin/