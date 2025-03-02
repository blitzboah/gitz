import javax.print.attribute.standard.NumberOfInterveningJobs;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;

public class GitUtils {

    // the code parses git commit msgs, reading in a format of key value list with msg - kvlm
    public static Map<byte[], byte[]> kvlmParse(byte[] raw, int start, Map<byte[], byte[]> dct){
        if(dct == null){
            dct = new HashMap<>();
        }

        //find next space and new line
        int spc = indexOf(raw, (byte) ' ', start);
        int nl = indexOf(raw, (byte) '\n', start);

        //if newline appears first or no space is found
        if(spc < 0 || nl < spc){
            if (nl == start) {
                dct.put(null, Arrays.copyOfRange(raw, start+1, raw.length));
                return dct;
            }
        }

        //read a kv pair and recurse
        byte[] key = Arrays.copyOfRange(raw, start, spc);

        int end = start;
        while (true){
            end = indexOf(raw, (byte) '\n', end+1);
            if (end+1 >= raw.length || raw[end+1] != ' '){
                break;
            }
        }

        byte[] value = Arrays.copyOfRange(raw, spc+1, end);
        dct.put(key,value);

        return kvlmParse(raw, end+1, dct);
    }

    public static int indexOf(byte[] arr, byte target, int s){
        for (int i = s; i < arr.length; i++) {
            if(arr[i] == target){
                return i;
            }
        }
        return -1;
    }

    public static byte[] kvlmSerialize(Map<byte[] , byte[]> kvlm){
        ByteArrayOutputStream ret = new ByteArrayOutputStream();
        try {
            for (Map.Entry<byte[], byte[]> entry: kvlm.entrySet()){
                byte[] key = entry.getKey();
                byte[] value = entry.getValue();

                // for commit msg
                if (key == null) continue;

                ret.write(key);
                ret.write(' ');
                ret.write(addSpaceAfterNewline(value));
                ret.write('\n');
            }

            ret.write('\n');
            ret.write(kvlm.get(null));

            return ret.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("error serializing kvlm");
        }
    }

    // this just for adding a new line lmao
    private static byte[] addSpaceAfterNewline(byte[] input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < input.length; i++) {
            output.write(input[i]);
            if (input[i] == '\n' && i != input.length - 1) {
                output.write((byte) ' ');
            }
        }
        return output.toByteArray();
    }

    public static void verifyTargetDirectory(File targetDir) throws Exception {
        if(targetDir.exists()){
            if(!targetDir.isDirectory()){
                throw new Exception("not a directory: " + targetDir.getPath());
            }
            if(targetDir.list() != null && targetDir.list().length > 0){
                throw new Exception("not empty: " + targetDir.getPath());
            }
        }
        else{
            if(!targetDir.mkdirs()){
                throw new Exception("could not create directory: " + targetDir.getPath());
            }
        }
    }

    public static String refResolve(Path repo, Path ref) throws IOException {
        Path path = repo.resolve(ref);

        if(!Files.isRegularFile(path)) return null;

        String data = Files.readString(path).trim();

        if(data.startsWith("ref: ")){
            // extract the reference path without the "ref: " prefix
            String refPath = data.substring(5).trim();
            // create a Path object from the reference string
            Path newRefPath = Path.of(".gitz/"+refPath);
            // recursively resolve the new reference
            return refResolve(repo, newRefPath);
        }
        else{
            return data;
        }
    }

    public static Map<String, Object> refList(Path repo, Path path) throws IOException {
        if(path == null){
            path = repo.resolve("refs");
        }

        Map<String, Object> ret = new TreeMap<>(); // using treemap to maintain order unlike hashmap can't maintain order type shi

        File[] files = path.toFile().listFiles();
        if(files == null) return ret;

        for (File f: files){
            if(f.isDirectory()){
                ret.put(f.getName(), refList(repo, f.toPath()));
            }
            else{
                ret.put(f.getName(), refResolve(repo, f.toPath()));
            }
        }

        return ret;
    }

    public static void showRef(Path repo, Map<String, Object> refs, boolean withHash, String prefix){
        if(!prefix.isEmpty()){
            prefix = prefix + "/";
        }

        for(Map.Entry<String, Object> entry: refs.entrySet()){
            String key = entry.getKey();
            Object val = entry.getValue();

            if(val instanceof String){
                if(withHash){
                    System.out.println(val+" "+prefix + key);
                }
                else{
                    System.out.println(prefix + key);
                }
            }
            else if(val instanceof Map){
                // recursive call for nested references / dir
                showRef(repo, (Map<String, Object>) val, withHash, prefix+key);
            }
        }
    }

    public static String commitCreate(Path repo, String tree, String parent, String author, OffsetDateTime timestamp, String message) throws Exception{
        GitCommit commit = new GitCommit();

        Map<byte[], byte[]> kvlm = new HashMap<>();

        kvlm.put("tree".getBytes(StandardCharsets.US_ASCII), tree.getBytes(StandardCharsets.US_ASCII));

        if(parent != null && !parent.isEmpty()){
            kvlm.put("parent".getBytes(StandardCharsets.US_ASCII), parent.getBytes(StandardCharsets.US_ASCII));
        }

        message = message.trim();

        int offsetSeconds = timestamp.getOffset().getTotalSeconds();
        int hours = offsetSeconds / 3600;
        int minutes = (offsetSeconds % 3600) / 60;
        String tz = String.format("%s%02d%02d", offsetSeconds >= 0 ? "+" : "-", Math.abs(hours), Math.abs(minutes));

        String authorInfo = author + " " + timestamp.toEpochSecond() + " " + tz;
        kvlm.put("author".getBytes(StandardCharsets.UTF_8), authorInfo.getBytes(StandardCharsets.UTF_8));
        kvlm.put("committer".getBytes(StandardCharsets.UTF_8), authorInfo.getBytes(StandardCharsets.UTF_8));

        kvlm.put(null, message.getBytes(StandardCharsets.UTF_8));

        byte[] commitData = kvlmSerialize(kvlm);
        return GitRepository.objectWrite(new GitCommit(commitData));
    }

    public static Properties gitconfigRead() throws IOException {
        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
        if (xdgConfigHome == null) {
            xdgConfigHome = System.getProperty("user.home") + File.separator + ".config";
        }

        List<String> configFiles = new ArrayList<>();
        configFiles.add(Paths.get(xdgConfigHome, "git", "config").toString());
        configFiles.add(System.getProperty("user.home") + File.separator + ".gitconfig");

        // expand paths with ~ if needed
        for (int i = 0; i < configFiles.size(); i++) {
            String path = configFiles.get(i);
            if (path.startsWith("~")) {
                configFiles.set(i, System.getProperty("user.home") + path.substring(1));
            }
        }

        Properties config = new Properties();

        for (String configPath : configFiles) {
            File configFile = new File(configPath);
            if (configFile.exists() && configFile.isFile()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    // load the properties in INI format (Git config format)
                    loadGitConfig(config, fis);
                }
            }
        }

        return config;
    }

    public static String gitconfigUserGet(Properties config) {
        String userName = config.getProperty("user.name");
        String userEmail = config.getProperty("user.email");

        if (userName != null && userEmail != null) {
            return userName + " <" + userEmail + ">";
        }

        return null;
    }

    private static void loadGitConfig(Properties properties, FileInputStream fis) throws IOException {
        java.util.Scanner scanner = new java.util.Scanner(fis);
        String currentSection = null;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();

            // skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }

            // section header [section] or [section "subsection"]
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1).trim().toLowerCase();
                // handle subsections by replacing quotes and spaces for property format
                currentSection = currentSection.replace("\"", "").replace(" ", ".");
                continue;
            }

            // kv pair
            if (currentSection != null && line.contains("=")) {
                int equalsPos = line.indexOf('=');
                String key = line.substring(0, equalsPos).trim();
                String value = line.substring(equalsPos + 1).trim();

                // remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                // store as section.key format
                properties.setProperty(currentSection + "." + key, value);
            }
        }

        scanner.close();
    }
}