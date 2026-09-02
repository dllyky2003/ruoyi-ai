package org.ruoyi.constant;

public class FileTypeConstants {
    public static final String TXT = "txt";
    public static final String CSV = "csv";
    public static final String MD = "md";
    public static final String DOC = "doc";
    public static final String DOCX = "docx";
    public static final String PDF = "pdf";
    public static final String XLS = "xls";
    public static final String XLSX = "xlsx";

    public static final String LOG = "log";
    public static final String XML = "xml";

    public static final String JAVA = "java";
    public static final String HTML = "html";
    public static final String HTM = "htm";
    public static final String CSS = "css";
    public static final String JS = "js";
    public static final String PY = "py";
    public static final String CPP = "cpp";
    public static final String SQL = "sql";
    public static final String PHP = "php";
    public static final String RUBY = "ruby";
    public static final String C = "c";
    public static final String H = "h";
    public static final String HPP = "hpp";
    public static final String SWIFT = "swift";
    public static final String TS = "ts";
    public static final String RUST = "rs";
    public static final String PERL = "perl";
    public static final String SHELL = "shell";
    public static final String BAT = "bat";
    public static final String CMD = "cmd";

    public static final String PROPERTIES = "properties";
    public static final String INI = "ini";
    public static final String YAML = "yaml";
    public static final String YML = "yml";

    public static boolean isTextFile(String type) {
        return type.equalsIgnoreCase(TXT) || type.equalsIgnoreCase(CSV) || type.equalsIgnoreCase(PROPERTIES)
            || type.equalsIgnoreCase(INI) || type.equalsIgnoreCase(YAML) || type.equalsIgnoreCase(YML)
            || type.equalsIgnoreCase(LOG) || type.equalsIgnoreCase(XML);
    }

    public static boolean isCodeFile(String type) {
        return type.equalsIgnoreCase(JAVA) || type.equalsIgnoreCase(HTML) || type.equalsIgnoreCase(HTM) || type.equalsIgnoreCase(JS) || type.equalsIgnoreCase(PY)
            || type.equalsIgnoreCase(CPP) || type.equalsIgnoreCase(SQL) || type.equalsIgnoreCase(PHP) || type.equalsIgnoreCase(RUBY)
            || type.equalsIgnoreCase(C) || type.equalsIgnoreCase(H) || type.equalsIgnoreCase(HPP) || type.equalsIgnoreCase(SWIFT)
            || type.equalsIgnoreCase(TS) || type.equalsIgnoreCase(RUST) || type.equalsIgnoreCase(PERL) || type.equalsIgnoreCase(SHELL)
            || type.equalsIgnoreCase(BAT) || type.equalsIgnoreCase(CMD) || type.equalsIgnoreCase(CSS);
    }

    public static boolean isMdFile(String type) {
        return type.equalsIgnoreCase(MD);
    }

    public static boolean isWord(String type) {
        return type.equalsIgnoreCase(DOC) || type.equalsIgnoreCase(DOCX);
    }

    public static boolean isPdf(String type) {
        return type.equalsIgnoreCase(PDF);
    }

    public static boolean isExcel(String type) {
        return type.equalsIgnoreCase(XLS) || type.equalsIgnoreCase(XLSX);
    }

}
