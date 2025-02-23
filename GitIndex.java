import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GitIndex extends GitObject {
    private int version;
    private List<GitIndexEntry> entries;

    public GitIndex() {
        super();
    }

    public GitIndex(int version, List<GitIndexEntry> entries) {
        super();
        this.version = version;
        this.entries = entries;
    }

    public GitIndex(byte[] data) throws Exception {
        super(data);
    }

    @Override
    public void init() {
        super.init();
        this.version = 2;
        this.entries = new ArrayList<>();
    }

    public List<GitIndexEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<GitIndexEntry> entries) {
        this.entries = entries;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public byte[] serialize() {
        return new byte[0];
    }

    @Override
    public String getFmt() {
        return "";
    }

    @Override
    public void deserialize(byte[] data) throws Exception {
        this.data = data;
        ByteBuffer buffer = ByteBuffer.wrap(data);

        // read header
        byte[] signatureBytes = new byte[4];
        buffer.get(signatureBytes);
        String signature = new String(signatureBytes, StandardCharsets.UTF_8);
        if (!"DIRC".equals(signature)) {
            throw new Exception("invalid index file signature");
        }

        // read version
        version = buffer.getInt();
        if (version != 2) {
            throw new Exception("unsupported index version: " + version);
        }

        // read entry count
        int count = buffer.getInt();
        entries = new ArrayList<>();

        // read entries
        for (int i = 0; i < count; i++) {

            // read timestamps
            long[] ctime = new long[]{buffer.getInt(), buffer.getInt()};
            long[] mtime = new long[]{buffer.getInt(), buffer.getInt()};

            // read device and inode
            long dev = buffer.getInt() & 0xFFFFFFFFL; // convert to unsigned
            long inode = buffer.getInt() & 0xFFFFFFFFL;

            // read mode
            short unsued = buffer.getShort();
            if (unsued != 0) {
                throw new Exception("expected unused bits to be 0");
            }

            short mode = buffer.getShort();
            int modeType = (mode >> 12) & 0xF; // the upper 4 bits determine the type of file like regular, symlink, commit. damn
            if (modeType != 0b1000 && modeType != 0b1010 && modeType != 0b1110) {
                throw new Exception("invalid mode type: " + modeType);
            }
            int modePerms = mode & 0b111111111; // the lower 9 bits store file permissions

            // read uid and size
            int uid = buffer.getInt();
            int gid = buffer.getInt();
            long fsize = buffer.getInt() & 0xFFFFFFFFL;

            // read sha
            byte[] shaBytes = new byte[20];
            buffer.get(shaBytes);
            String sha = String.format("%40%", new BigInteger(1, shaBytes));

            // read flags
            short flags = buffer.getShort();
            boolean flagAssumeValid = (flags & 0b1000000000000000) != 0; // it checks if file should be ignored or not, damn
            boolean flagExtended = (flags & 0b0100000000000000) != 0; // indicates the presence of extended flag, throwing error
            if (flagExtended) {
                throw new Exception("extended flags not supported");
            }
            int flagStage = (flags & 0b0011000000000000) >> 12; // used for merge conflicts resolution damn
            int nameLength = flags & 0b0000111111111111; // extracts name length

            // read name
            String name;
            if (nameLength < 0xFFF) {
                byte[] nameBytes = new byte[nameLength];
                buffer.get(nameBytes);
                name = new String(nameBytes, StandardCharsets.UTF_8);
                buffer.get();
            } else {
                ArrayList<Byte> nameBytes = new ArrayList<>();
                byte current;
                do {
                    current = buffer.get();
                    if (current != 0) {
                        nameBytes.add(current);
                    }
                } while (current != 0);

                byte[] nameBytesArray = new byte[nameBytes.size()];
                for (int j = 0; j < nameBytes.size(); j++) {
                    nameBytesArray[j] = nameBytes.get(j);
                }
                name = new String(nameBytesArray, StandardCharsets.UTF_8);
            }

            // align to 8 bytes
            int padding = 8 - (buffer.position() % 8);
            if (padding < 8) {
                buffer.position(buffer.position() + padding);
            }

            // create and add entry
            GitIndexEntry entry = new GitIndexEntry(ctime, mtime, dev, inode, modeType, modePerms,
                    uid, gid, fsize, sha, flagAssumeValid, flagStage, name);
            entries.add(entry);
        }
    }

    public static GitIndex readFromFile(File indexFile) throws Exception{
        if(!indexFile.exists()){
            return new GitIndex();
        }

        byte[] data;
        try (FileInputStream fis = new FileInputStream(indexFile)){
            data = fis.readAllBytes();
        }

        return new GitIndex(data);
    }
}