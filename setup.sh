#!/bin/bash

# compile all java files
javac *.java

# package into a jar
jar cfm gitz.jar MANIFEST.MF *.class def.png

# move jar to /usr/local/bin
sudo mv gitz.jar /usr/local/bin/gitz

# add alias to bashrc/zshrc
if [ -n "$ZSH_VERSION" ]; then
    SHELL_CONFIG="$HOME/.zshrc"
else
    SHELL_CONFIG="$HOME/.bashrc"
fi

echo "alias gitz='java -jar /usr/local/bin/gitz'" >> "$SHELL_CONFIG"
source "$SHELL_CONFIG"