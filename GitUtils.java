import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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

    private static int indexOf(byte[] arr, byte target, int s){
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
}
