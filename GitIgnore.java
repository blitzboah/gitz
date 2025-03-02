import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class GitIgnore {
    private List<List<GitIgnorePattern>> absolute;
    private Map<String, List<GitIgnorePattern>> scoped;

    public GitIgnore(List<List<GitIgnorePattern>> absolute, Map<String, List<GitIgnorePattern>> scoped) {
        this.absolute = absolute;
        this.scoped = scoped;
    }

    public static class GitIgnorePattern{
        private String pattern;
        private boolean inclusive;

        public GitIgnorePattern(String pattern, boolean inclusive) {
            this.pattern = pattern;
            this.inclusive = inclusive;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public boolean isInclusive() {
            return inclusive;
        }

        public void setInclusive(boolean inclusive) {
            this.inclusive = inclusive;
        }
    }

    public static boolean checkIgnore(GitIgnore rules, String path){
        if(Paths.get(path).isAbsolute()){
            throw new IllegalArgumentException("path must be relative to repository root");
        }

        Boolean scopedResult = checkIgnoredScoped(rules.scoped, path);
        if(scopedResult != null) return scopedResult;

        return checkIgnoreAbsolute(rules.absolute, path);
    }

    public static GitIgnorePattern gitIgnoreParse(String raw){
        if(raw == null){
            return null;
        }

        raw = raw.strip();

        if(raw.isEmpty() || raw.charAt(0) == '#'){
            return null;
        }
        else if(raw.charAt(0) == '!'){
            return new GitIgnorePattern(raw.substring(1), false);
        }
        else if(raw.charAt(0) == '\\') {
            return new GitIgnorePattern(raw.substring(1), true);
        }
        else{
            return new GitIgnorePattern(raw, true);
        }
    }

    public static List<GitIgnorePattern> gitIgnoreParse(List<String> lines){
        List<GitIgnorePattern> list = new ArrayList<>();

        for(String line: lines){
            GitIgnorePattern parsed = gitIgnoreParse(line);
            if(parsed != null){
                list.add(parsed);
            }
        }

        return list;
    }

    public static GitIgnore gitIgnoreRead(Path repo) throws Exception {
        List<List<GitIgnorePattern>> absolute = new ArrayList<>();
        Map<String, List<GitIgnorePattern>> scoped = new HashMap<>();

        Path repoFile = repo.resolve(".gitz/info/exclude");
        if(Files.exists(repoFile)){
            List<String> lines = Files.readAllLines(repoFile);
            absolute.add(gitIgnoreParse(lines));
        }

        String configHome;
        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");

        if(xdgConfigHome != null && !xdgConfigHome.isEmpty()){
            configHome = xdgConfigHome;
        }
        else{
            configHome = System.getProperty("user.home") + "/.config";
        }

        Path globalFile = Path.of(configHome, "git/ignore");
        if(Files.exists(globalFile)){
            List<String> lines = Files.readAllLines(globalFile);
            absolute.add(gitIgnoreParse(lines));
        }

        GitIndex index = GitIndex.readFromFile(repo.resolve(".gitz/index").toFile());

        for (GitIndexEntry entry : index.getEntries()) {
            if (entry.getName().equals(".gitzignore") ||
                    entry.getName().endsWith("/.gitzignore")) {

                String dirName = new File(entry.getName()).getParent();
                if (dirName == null) {
                    dirName = "";
                }

                GitObject contents = GitRepository.objectRead(entry.getSha());
                if (contents instanceof GitBlob) {
                    String blobContent = new String(contents.serialize(), StandardCharsets.UTF_8);
                    List<String> lines = Arrays.asList(blobContent.split("\n"));
                    scoped.put(dirName, gitIgnoreParse(lines));
                }
            }
        }
        return new GitIgnore(absolute, scoped);
    }

    private static Boolean checkIgnore1(List<GitIgnorePattern> rules, String path){
        Boolean result = null;
        for(GitIgnorePattern rule : rules){
            if(FileSystems.getDefault().getPathMatcher("glob: "+rule.getPattern()).matches(Path.of(path))){
                result = rule.isInclusive();
            }
        }
        return result;
    }

    private static Boolean checkIgnoredScoped(Map<String, List<GitIgnorePattern>> rules, String path){
        String parent = new File(path).getParent();

        while (parent != null) {
            if (rules.containsKey(parent)) {
                Boolean result = checkIgnore1(rules.get(parent), path);
                if (result != null) {
                    return result;
                }
            }

            File parentFile = new File(parent);
            parent = parentFile.getParent();
        }

        return null;
    }

    private static boolean checkIgnoreAbsolute(List<List<GitIgnorePattern>> rules, String path){
        for(List<GitIgnorePattern> rule : rules){
            Boolean result = checkIgnore1(rule, path);
            if(result != null) return result;
        }
        return false;
    }

    public List<List<GitIgnorePattern>> getAbsolute() {
        return absolute;
    }

    public void setAbsolute(List<List<GitIgnorePattern>> absolute) {
        this.absolute = absolute;
    }

    public Map<String, List<GitIgnorePattern>> getScoped() {
        return scoped;
    }

    public void setScoped(Map<String, List<GitIgnorePattern>> scoped) {
        this.scoped = scoped;
    }
}
