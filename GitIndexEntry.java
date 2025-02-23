public class GitIndexEntry extends GitObject{

    private long[] ctime; // last time file's metadata changed
    private long[] mtime; // last time file's data changed
    private long dev; // id of device containing file
    private long ino; // file's inode number
    private int modeType; // object type - regular, symlink, etc
    private int modePerms; // obj permissions
    private int uid; // user id of owner
    private int gid; // group id of owner
    private long fsize; // size of object
    private String sha; // obj sha
    private boolean flagAssumeValid;
    private int flagStage;
    private String name; // name of obj

    public GitIndexEntry(){
        super();
    }

    public GitIndexEntry(long[] ctime, long[] mtime, long dev, long ino,
                         int modeType, int modePerms, int uid, int gid,
                         long fsize, String sha, boolean flagAssumeValid,
                         int flagStage, String name) {
        this.ctime = ctime;
        this.mtime = mtime;
        this.dev = dev;
        this.ino = ino;
        this.modeType = modeType;
        this.modePerms = modePerms;
        this.uid = uid;
        this.gid = gid;
        this.fsize = fsize;
        this.sha = sha;
        this.flagAssumeValid = flagAssumeValid;
        this.flagStage = flagStage;
        this.name = name;
    }

    @Override
    public byte[] serialize() {
        return new byte[0];
    }

    @Override
    public void deserialize(byte[] data) throws Exception {

    }

    @Override
    public String getFmt() {
        return "index";
    }

    public long[] getCtime() {
        return ctime;
    }

    public void setCtime(long[] ctime) {
        this.ctime = ctime;
    }

    public long[] getMtime() {
        return mtime;
    }

    public void setMtime(long[] mtime) {
        this.mtime = mtime;
    }

    public long getDev() {
        return dev;
    }

    public void setDev(long dev) {
        this.dev = dev;
    }

    public long getIno() {
        return ino;
    }

    public void setIno(long ino) {
        this.ino = ino;
    }

    public int getModeType() {
        return modeType;
    }

    public void setModeType(int modeType) {
        this.modeType = modeType;
    }

    public int getModePerms() {
        return modePerms;
    }

    public void setModePerms(int modePerms) {
        this.modePerms = modePerms;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public int getGid() {
        return gid;
    }

    public void setGid(int gid) {
        this.gid = gid;
    }

    public long getFsize() {
        return fsize;
    }

    public void setFsize(long fsize) {
        this.fsize = fsize;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public boolean isFlagAssumeValid() {
        return flagAssumeValid;
    }

    public void setFlagAssumeValid(boolean flagAssumeValid) {
        this.flagAssumeValid = flagAssumeValid;
    }

    public int getFlagStage() {
        return flagStage;
    }

    public void setFlagStage(int flagStage) {
        this.flagStage = flagStage;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
