package com.hfr.world.earth.geo;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/** Bounded, strict JSON records and stable identities, with no world or network access. */
final class XFEarthGeoJson {
    static final Gson GSON=new Gson();
    private XFEarthGeoJson() { }
    static JsonObject object(byte[] bytes) throws IOException {
        return object(bytes,524288);
    }
    static JsonObject object(byte[] bytes,int tokens) throws IOException {
        try(JsonReader reader=new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            reader.setLenient(false);
            JsonElement result=value(reader,0,new int[]{tokens});
            if(!result.isJsonObject()||reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Expected one JSON object");
            return result.getAsJsonObject();
        } catch(IllegalArgumentException e) { throw new IOException("Invalid JSON value",e); }
    }
    private static JsonElement value(JsonReader reader,int depth,int[] budget) throws IOException {
        if(depth>32||--budget[0]<0)throw new IOException("JSON nesting/token limit exceeded");
        switch(reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object=new JsonObject();reader.beginObject();
                while(reader.hasNext()) {
                    String key=reader.nextName();
                    if(key.length()>1024||object.has(key))throw new IOException("Oversize or duplicate JSON key");
                    object.add(key,value(reader,depth+1,budget));
                }
                reader.endObject();return object;
            case BEGIN_ARRAY:
                JsonArray array=new JsonArray();reader.beginArray();
                while(reader.hasNext())array.add(value(reader,depth+1,budget));
                reader.endArray();return array;
            case STRING:return new JsonPrimitive(reader.nextString());
            case NUMBER:
                String number=reader.nextString();if(number.length()>128)throw new IOException("Oversize JSON number");
                return new JsonPrimitive(new BigDecimal(number));
            case BOOLEAN:return new JsonPrimitive(reader.nextBoolean());
            case NULL:reader.nextNull();return JsonNull.INSTANCE;
            default:throw new IOException("Invalid JSON token");
        }
    }
    static JsonObject file(File file,int limit) throws IOException {
        try(InputStream in=new FileInputStream(file)) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
            for(int n;(n=in.read(buffer))!=-1;) { if(bytes.size()+n>limit)throw new IOException("Oversize JSON file: "+file.getName());bytes.write(buffer,0,n); }
            return object(bytes.toByteArray());
        }
    }
    static String text(JsonObject object,String key) {
        JsonElement value=object.get(key);
        if(value==null||!value.isJsonPrimitive()||value.isJsonNull())throw new IllegalArgumentException("Missing scalar "+key);
        return value.getAsString();
    }
    static String canonical(JsonElement value) {
        if(value.isJsonObject()) {
            JsonObject sorted=new JsonObject();TreeMap<String,JsonElement> keys=new TreeMap<String,JsonElement>();
            for(Map.Entry<String,JsonElement> entry:value.getAsJsonObject().entrySet())keys.put(entry.getKey(),entry.getValue());
            for(Map.Entry<String,JsonElement> entry:keys.entrySet())sorted.add(entry.getKey(),parseCanonical(entry.getValue()));
            return GSON.toJson(sorted);
        }
        if(value.isJsonArray()) {
            JsonArray array=new JsonArray();for(JsonElement entry:value.getAsJsonArray())array.add(parseCanonical(entry));return GSON.toJson(array);
        }
        return GSON.toJson(value);
    }
    private static JsonElement parseCanonical(JsonElement value) { return new JsonParser().parse(canonical(value)); }
    static MessageDigest digest() {
        try{return MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static String hash(String text) { return hex(digest().digest(text.getBytes(StandardCharsets.UTF_8))); }
    static String hex(byte[] bytes) { StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return value.toString(); }
    static String hash(File file,XFEarthGeoImporter.Control control) throws IOException {
        MessageDigest hash=digest();byte[] buffer=new byte[65536];
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))) {
            for(int n;(n=in.read(buffer))!=-1;) { control.check();hash.update(buffer,0,n); }
        }
        return hex(hash.digest());
    }
    static void writeNew(File file,JsonElement value) throws IOException {
        byte[] bytes=(GSON.toJson(value)+"\n").getBytes(StandardCharsets.UTF_8);
        try(OutputStream out=java.nio.file.Files.newOutputStream(file.toPath(),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE)) {out.write(bytes);}
    }

    /** RFC 8142 RS-delimited JSON (including pretty records), or one complete JSON object per line. */
    static final class Records implements Closeable {
        private final PushbackInputStream in;
        private final int limit;
        private final XFEarthGeoImporter.Control control;
        private boolean sequence,first=true,eof;
        long ordinal;
        boolean oversize;
        Records(InputStream in,int limit,XFEarthGeoImporter.Control control){this.in=new PushbackInputStream(new BufferedInputStream(in),1);this.limit=limit;this.control=control;}
        byte[] next() throws IOException {
            if(eof)return null;control.check();oversize=false;
            if(first) {
                int b;do{b=in.read();}while(b==32||b==9||b==10||b==13);
                if(b<0){eof=true;return null;}sequence=b==30;if(!sequence)in.unread(b);first=false;
            }
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();int b;long count=0;
            while((b=in.read())!=-1) {
                if((sequence&&b==30)||(!sequence&&b==10))break;
                if(++count<=limit)bytes.write(b);else oversize=true;
                if((count&65535)==0)control.check();
            }
            if(b<0)eof=true;
            if(count==0&&!oversize&&eof)return null;
            ordinal++;return bytes.toByteArray();
        }
        @Override public void close() throws IOException {in.close();}
    }
}
