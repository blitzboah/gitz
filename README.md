# gitz

## installation

to install `gitz` and make it executable globally, follow these steps:

### option 1: manual installation

**step 1: compile and package**
```sh
javac *.java
jar cfm gitz.jar MANIFEST.MF *.class def.jpg
```

**step 2: move to a global path**
```sh
sudo mv gitz.jar /usr/local/bin/gitz
```

**step 3: make an alias for convenience**
```sh
echo 'alias gitz="java -jar /usr/local/bin/gitz"' >> ~/.bashrc
source ~/.bashrc
```

### option 2: using setup.sh

run the provided setup script:
```sh
chmod +x setup.sh
./setup.sh
```

## initializing a repository

to create a new gitz repository, run:

```sh
gitz init
```

this will create a `.gitz` directory with necessary subdirectories and configuration files. it will also copy `def.jpg` into `.gitz/img/`.

## available commands

### repository setup
* `gitz init` → initializes a new repository

### working with objects
* `gitz cat-file <object> [type]` → displays the contents of an object
* `gitz hash-object [-w] -t <type> <file>` → hashes an object and optionally writes it

### tree and commit navigation
* `gitz ls-tree <tree-hash>` → lists the contents of a tree object
* `gitz checkout <commit-hash> <path>` → checks out a commit into a directory
* `gitz log` → shows commit history

### branching and references
* `gitz show-ref` → shows references
* `gitz tag <tag-name> [commit]` → creates a tag

### parsing objects
* `gitz rev-parse <name> [-t type]` → resolves a name to an object id

### working directory and index
* `gitz ls-files [-v]` → lists tracked files
* `gitz check-ignore <file>` → checks if a file is ignored
* `gitz status` → shows the working directory status
* `gitz add <file>` → stages a file for commit
* `gitz rm <file>` → removes a file from tracking

### committing changes
* `gitz commit -m <message>` → creates a commit with an defautlt blackbeard image hehe
* `gitz commit -m <message> -i <image_path>` → creates a commit with an image

### debugging and index inspection
* `gitz show-index` → shows the index contents
* `gitz dump-index` → dumps the raw index file

## usage examples

### initializing and committing changes
```sh
gitz init
echo "hello world" > file.txt
gitz add file.txt
gitz commit -m "init"
```
