package viewtool.usb_can_test;

public final class Helper {


    private final static char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};//十六进制的组成元素

    /**
     * 字节数组转16进制String，无分隔，如：FE00120F0E
     *
     * @param array  字节数组
     * @param offset 起始
     * @param length 长度
     * @return
     */
    public static String toHexString(byte[] array, int offset, int length) {
        char[] buf = new char[length * 2];

        int bufIndex = 0;
        for (int i = offset; i < offset + length; i++) {
            byte b = array[i];
            buf[bufIndex++] = HEX_DIGITS[(b >>> 4) & 0x0F];
            buf[bufIndex++] = HEX_DIGITS[b & 0x0F];
        }

        return new String(buf);
    }

}
