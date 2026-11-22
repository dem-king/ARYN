package com.aryn.cloud.product.support;
import java.util.*;
import java.util.function.*;
public final class CatalogIndexRules {
    private CatalogIndexRules(){ }
    public static boolean compatible(String a,int ad,String b,int bd){return Objects.equals(a,b) && ad==bd;}
    public static void walkPages(BiFunction<String,Integer,List<String>> loader,Consumer<List<String>> sink,int size){
        String cursor=null;Set<String> seen=new HashSet<>();
        while(true){List<String> page=loader.apply(cursor,size);if(page.isEmpty()) return;
            for(String id:page) if(!seen.add(id)) throw new IllegalStateException("目录游标未前进");
            sink.accept(page);cursor=page.get(page.size()-1);if(page.size()<size) return;
        }
    }
    public static String fingerprint(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException();}}
}
