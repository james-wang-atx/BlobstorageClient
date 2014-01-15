/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
//package org.apache.http.examples.entity.mime;

import java.io.File;
import java.io.PrintWriter;
import java.lang.String;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * Using multipart/form encoded POST request to send jpeg image to appengine blobstore
 */
public class ClientMultipartFormPost {
    private static final String GetNextUploadURL = "http://10.10.1.6:8080/uploadURL";
    private static final String UploadURLCacheFile = "C:\\UploadURLCache.txt";              //TODO: use /dev/shm
    
    public static String ReadFileIntoString(String path, Charset encoding) throws Exception 
    {
        byte[] encoded = Files.readAllBytes( Paths.get( path ) );
        return encoding.decode( ByteBuffer.wrap( encoded ) ).toString();
    }
    
    public static void WriteStringIntoFile( String value, String path ) throws Exception
    {
        PrintWriter writer = new PrintWriter( path );
        writer.print( value );
        writer.close();
    }
    
    public static String QueryUploadURL( CloseableHttpClient httpclient ) throws Exception
    {
        String result = null;
        
        try
        {
            HttpGet httpget = new HttpGet( GetNextUploadURL );
            CloseableHttpResponse response = httpclient.execute( httpget );
            try
            {
                //System.out.println("----------------------------------------");
                //System.out.println(response.getStatusLine());
                HttpEntity resEntity = response.getEntity();
                
                if (resEntity != null)
                {
                    //System.out.println("Response content length: " + resEntity.getContentLength());
                    //System.out.println("Response: " + resEntity.getContent());
                    
                    String entityString = EntityUtils.toString( resEntity ); 
                    //System.out.println( entityString );
                    
                    String[] splitLines = entityString.split("[\\r\\n]+");
                    System.out.println("QUERYUPLOADURL: " + splitLines[0]);
        
                    // use this for next try
                    result = splitLines[0];
                }
                
                EntityUtils.consume(resEntity);
            }
            finally
            {
                response.close();
            }
        }
        finally
        {
        }
        
        return result;
    }
    
    public static void main(String[] args) throws Exception
    {    
        if (args.length != 2)
        {
            System.out.println("File path and blobstore filename not given");
            System.exit(1);
        }
        
        //System.out.println("args[0]=" + args[0] + ", args[1]" + args[1]);
        
        CloseableHttpClient httpclient = HttpClients.createDefault();
        
        try {
            String nextUploadURL = null;
                        
            String urlFromFile = ReadFileIntoString( UploadURLCacheFile, Charset.defaultCharset() );            

            if(urlFromFile == null || urlFromFile.isEmpty())
            {
                nextUploadURL = QueryUploadURL( httpclient );
                System.out.println("START PROGRAM: GOT UPLOADURL FROM SERVER: '" + nextUploadURL + "'");
            }
            else
            {
                nextUploadURL = urlFromFile;
                System.out.println("START PROGRAM: GOT UPLOADURL FROM FILE: '" + nextUploadURL + "'");
            }
            
            if( nextUploadURL != null && !nextUploadURL.isEmpty() )
            {
                for( int i = 0; i<1; i++ )//while( true )
                {
                    System.out.println("START OF LOOP UPLOADURL: '" + nextUploadURL + "'");
                    
                    HttpPost httppost = new HttpPost( nextUploadURL );
            
                    httppost.setHeader("enctype", "multipart/form-data");
                    
                    String name          = "roverX";                // magic, form-data, name that our upload server looks for
                    String contenttype   = "image/jpeg";            // mime-type
                    File   imageFile     = new File( args[0] );
                    String filename      = args[1];                 // magic filename used by blobstore, which we need rover's to set to this strict
                                                                    //   format "rover_<id>_cam<number>.jpg" due to search parameters used in search page.
                                                                    //   e.g. "rover_1_cam1.jpg"
                                                                    //
                                                                    //   Theoretically, the <id> can be anything that doesn't contain understore, but, for now,
                                                                    //   it should be an ordinal value equivalent to a "serial number".
                                                                    // The camera <number> should likewise be ordinal value
                                                                    //   (which should be always 1 since, we are only installing 1 camera on each rover.)
                    
                    HttpEntity reqEntity = MultipartEntityBuilder.create()
                            .addBinaryBody( name,
                                            imageFile,
                                            ContentType.create( contenttype ),
                                            filename )
                            .build();
        
                    httppost.setEntity(reqEntity);
        
                    System.out.println("executing request " + httppost.getRequestLine());
                    CloseableHttpResponse response = httpclient.execute(httppost);
                    
                    try {
                        //System.out.println("----------------------------------------");
                        System.out.println(response.getStatusLine());
                        HttpEntity resEntity = response.getEntity();
                        
                        if (resEntity != null)
                        {
                            //System.out.println("Response content length: " + resEntity.getContentLength());
                            //System.out.println("Response: " + resEntity.getContent());
                            
                            String entityString = EntityUtils.toString( resEntity ); 
                            //System.out.println( entityString );
                            
                            String urlMarker = "NEXT-uploadURL:";
                            int nextURLPosition = entityString.indexOf( urlMarker );
                            String nextUploadURLandMore = entityString.substring( nextURLPosition + urlMarker.length() );
                            String[] splitLines = nextUploadURLandMore.split("[\\r\\n]+");
                            System.out.println("NEXT UPLOADURL: " + splitLines[0]);
                
                            // use this for next try
                            nextUploadURL = splitLines[0].trim();
                            
                            WriteStringIntoFile( nextUploadURL, UploadURLCacheFile );
                        }
                        
                        EntityUtils.consume(resEntity);
                    }
                    finally
                    {
                        response.close();
                    }                
                }
            }
            else
            {
                System.out.println("ERROR! unable to determine the upload URL.");
            }
        }
        finally
        {
            httpclient.close();
        }
    }    
}