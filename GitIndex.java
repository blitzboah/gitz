import java.io.*;
import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

        //System.out.println("reading " + count + " entries from index file");

        // read entries
        for (int i = 0; i < count; i++) {
            //System.out.println("reading entry " + (i+1) + " at position " + buffer.position());

            // read timestamps
            long[] ctime = new long[]{buffer.getInt(), buffer.getInt()};
            long[] mtime = new long[]{buffer.getInt(), buffer.getInt()};

            // read device and inode
            long dev = buffer.getInt() & 0xFFFFFFFFL; // convert to unsigned
            long inode = buffer.getInt() & 0xFFFFFFFFL;

            // read mode
            int mode = buffer.getInt(); // read the entire mode field (4 bytes)
            int modeType = (mode >> 12) & 0xF; // extract the type from the upper 4 bits of the mode
            int modePerms = mode & 0x1FF; // extract permissions from the lower 9 bits

            if (modeType != 0b1000 && modeType != 0b1010 && modeType != 0b1110) {
                //System.out.println("warning: unexpected mode type " + modeType + " possibly an empty file, defaulting to regular file");
                modeType = 0b1000;
            }

            // read uid and size
            int uid = buffer.getInt();
            int gid = buffer.getInt();
            long fsize = buffer.getInt() & 0xFFFFFFFFL;

            // read sha
            byte[] shaBytes = new byte[20];
            buffer.get(shaBytes);
            String sha = String.format("%040x", new BigInteger(1, shaBytes));

            // read flags
            short flags = buffer.getShort();
            boolean flagAssumeValid = (flags & 0x8000) != 0; // highest bit (bit 15)
            boolean flagExtended = (flags & 0x4000) != 0; // bit 14
            int flagStage = (flags & 0x3000) >> 12; // bits 12-13
            int nameLength = flags & 0x0FFF; // bits 0-11

            // If we encounter an extended flag, just log it but don't skip the entry
            if (flagExtended) {
                //System.out.println("entry has extended flags - but continuing to parse");
            }

            // read name
            String name;
            if (nameLength < 0xFFF) {
                byte[] nameBytes = new byte[nameLength];
                buffer.get(nameBytes);
                // skip the null terminator
                buffer.get();
                name = new String(nameBytes, StandardCharsets.UTF_8);
            } else {
                // for longer names, read until null byte
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

            // calculate padding based on entrySize (same as during serialization)
            int entrySize = 62 + nameLength + 1;
            int padding = (8 - (entrySize % 8)) % 8;
            buffer.position(buffer.position() + padding);

            // create and add entry
            GitIndexEntry entry = new GitIndexEntry(ctime, mtime, dev, inode, modeType, modePerms,
                    uid, gid, fsize, sha, flagAssumeValid, flagStage, name);
            entries.add(entry);

            //System.out.println("added entry: " + name + " with SHA: " + sha);
        }

        //System.out.println("successfully read " + entries.size() + " entries");
    }

    public static GitIndex readFromFile(File indexFile) throws Exception {
        if (!indexFile.exists() || indexFile.length() == 0) { // check if file is empty
            System.out.println("index file not found or empty, initializing new index.");
            GitIndex newIndex = new GitIndex();
            newIndex.init();
            return newIndex;
        }

        byte[] data;
        try (FileInputStream fis = new FileInputStream(indexFile)) {
            data = fis.readAllBytes();
        }

        try {
            return new GitIndex(data);
        } catch (BufferUnderflowException e) {
            System.out.println("corrupt index file, reinitializing...");
            GitIndex newIndex = new GitIndex();
            newIndex.init();
            return newIndex;
        }
    }


    public static void indexWrite(Path repo, GitIndex index) {
        Path indexFilePath = repo.resolve(".gitz/index");

        try (FileOutputStream fos = new FileOutputStream(indexFilePath.toFile())) {
            // write header
            fos.write("DIRC".getBytes(StandardCharsets.US_ASCII));
            writePut(fos, index.getVersion(), 4); // version
            writePut(fos, index.getEntries().size(), 4); // number of entries

            //System.out.println("writing " + index.getEntries().size() + " entries to index file");

            // write entries
            for (GitIndexEntry e : index.getEntries()) {
                // timestamps
                writePut(fos, e.getCtime()[0], 4); // ctime seconds
                writePut(fos, e.getCtime()[1], 4); // ctime nanoseconds
                writePut(fos, e.getMtime()[0], 4); // mtime seconds
                writePut(fos, e.getMtime()[1], 4); // mtime nanoseconds

                // Device and inode
                writePut(fos, e.getDev(), 4);
                writePut(fos, e.getIno(), 4);

                // mode: write as a 4-byte field
                // first two bytes are unused (should be 0)
                // third byte contains the file type in its upper 4 bits
                // fourth byte contains the file permissions
                int modeField = 0;
                modeField |= (e.getModeType() & 0xF) << 12; // file type in upper 4 bits of 2 bytes
                modeField |= (e.getModePerms() & 0x1FF); // permissions in lower 9 bits
                writePut(fos, modeField, 4);

                // UID and GID
                writePut(fos, e.getUid(), 4);
                writePut(fos, e.getGid(), 4);

                // file size
                writePut(fos, e.getFsize(), 4);

                // sha-1 hash (20 bytes)
                byte[] shaBytes = hexStringToBytes(e.getSha());
                fos.write(shaBytes);

                // flags: assume valid, stage, and name length
                byte[] nameBytes = e.getName().getBytes(StandardCharsets.UTF_8);
                int flags = 0;
                if (e.isFlagAssumeValid()) {
                    flags |= 0x8000;  // set the highest bit for assume-valid flag (bit 15)
                }
                // DO NOT set the extended flag (bit 14) unless you're actually using extended features
                flags |= (e.getFlagStage() & 0x3) << 12;  // set the stage bits (bits 12-13)
                flags |= Math.min(nameBytes.length, 0xFFF);  // set the name length (bits 0-11)

                writePut(fos, flags, 2);

                // file name + null terminator
                fos.write(nameBytes);
                fos.write(0);

                // padding to align to 8 bytes
                int entrySize = 62 + nameBytes.length + 1; // fixed bytes + name + null byte
                int padding = (8 - (entrySize % 8)) % 8;
                fos.write(new byte[padding]);

                //System.out.println("wrote entry: " + e.getName() + " with SHA: " + e.getSha());
            }

            //System.out.println("index successfully written to " + indexFilePath);

        } catch (IOException e) {
            throw new RuntimeException("failed to write index file", e);
        }
    }

    private static byte[] hexStringToBytes(String hex) {
        byte[] bytes = new byte[20];
        for (int i = 0; i < 20; i++) {
            int startIdx = i * 2;
            if (startIdx + 2 <= hex.length()) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(startIdx, startIdx + 2), 16);
            }
        }
        return bytes;
    }

    public static void writePut(FileOutputStream fos, long val, int numBytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(numBytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        switch (numBytes){
            case 1 -> buffer.put((byte) val);
            case 2 -> buffer.putShort((short) val);
            case 4 -> buffer.putInt((int) val);
            case 8 -> buffer.putLong(val);
            default -> System.out.println("unsupported no of bytes: "+numBytes);
        }

        fos.write(buffer.array());
    }
}