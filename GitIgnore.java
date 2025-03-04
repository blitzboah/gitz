import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GitIgnore {
    private final List<Pattern> patterns;

    private GitIgnore() {
        this.patterns = new ArrayList<>();
    }

    public static GitIgnore gitIgnoreRead(Path repo) throws IOException {
        GitIgnore ignore = new GitIgnore();
        Path ignoreFile = repo.resolve(".gitzignore");

        if (Files.exists(ignoreFile)) {
            List<String> lines = Files.readAllLines(ignoreFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                boolean negate = line.startsWith("!");
                if (negate) {
                    line = line.substring(1).trim();
                }

                String regex = convertToRegex(line);
                Pattern pattern = Pattern.compile("^" + regex + "$");
                ignore.patterns.add(pattern);

            }
        }

        return ignore;
    }

    private static String convertToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();

        boolean isDirectoryPattern = pattern.endsWith("/");
        if (isDirectoryPattern) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                case '/':
                    regex.append("\\/");
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }

        if (isDirectoryPattern) {
            regex.append("(\\/.*)?");
        }

        return regex.toString();
    }

    public static boolean checkIgnore(GitIgnore ignore, String path) {
        if (ignore == null || ignore.patterns.isEmpty()) {
            return false;
        }
        path = path.replace("\\", "/");

        for (Pattern pattern : ignore.patterns) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    public List<Pattern> getPatterns() {
        return new ArrayList<>(patterns);
    }
}
