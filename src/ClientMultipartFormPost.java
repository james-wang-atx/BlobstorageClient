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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.String;
import java.nio.charset.Charset;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

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
public class ClientMultipartFormPost implements Runnable {
    //private static final String GetNextUploadURL = "http://10.10.1.2:8080/uploadURL"; //"http://10.2.100.29:8080/uploadURL";
//    private static final String UploadURLCacheFile = "C:\\Users\\james_000\\UploadURLCache.txt";              //TODO: use /dev/shm
//    private static final String UploadURLCacheFile = "C:\\Users\\JAMES_~1\\workspace\\git\\BlobstorageClient\\bin\\UploadURLCache.txt";
    private static final String UploadURLCacheFile = "/dev/shm/UploadURLCache.txt";
    
    private String _GetNextUploadURL;
    private CloseableHttpClient _httpclient;
    private String _nextUploadURL;
    private String _imageFilename;
    private String _blobstoreFilename;
    
    public ClientMultipartFormPost( CloseableHttpClient httpclient, String GetNextUploadURL, String nextUploadURL, String imageFilename, String blobstoreFilename )
    {
        this._GetNextUploadURL = GetNextUploadURL;
        this._httpclient = httpclient;
        this._nextUploadURL = nextUploadURL;
        this._imageFilename = imageFilename;
        this._blobstoreFilename = blobstoreFilename;
    }
    
    private static final String GPIOPath = "/sys/class/gpio";
    private static final String GPIOExportPath = GPIOPath + "/export";
    private static final int WaterSensorGPIONumber = 30;
    private static final int FlameSensorGPIONumber = 66;
    
    private boolean ExportGpio( int gpio )
    {
        String path = GPIOPath + "/gpio" + gpio;

        // check if already exported
        File f = new File(path);
        if( f.exists() )
        {
            return true;
        }
            
        try
        {
            BufferedWriter bw = new BufferedWriter ( new FileWriter ( GPIOExportPath ) );
            bw.write( "" + gpio );
            bw.close();
            
            return true;
        }
        catch(IOException e){
            System.out.println( "ExportGpio failed - gpio = " + gpio + ", path = " + GPIOExportPath );
        }
        
        return false;
    }
    
    private boolean SetGpioDirection( int gpio, boolean in )
    {
        String path = GPIOPath + "/gpio" + gpio + "/direction";
        
        try
        {
            BufferedWriter bw = new BufferedWriter ( new FileWriter ( path ) );
            
            if( in )
            {
                bw.write( "in" );            
            }
            else
            {
                bw.write( "out" );                            
            }
            bw.close();
            
            return true;
        }
        catch(IOException e){
            System.out.println( "SetGpioDirection failed - " + gpio + ", in = " + in + ", path = " + path );
        }
        
        return false;
    }
    
    private int GetGpioValue( int gpio )
    {
        int ret = -1;
        String path = GPIOPath + "/gpio" + gpio + "/value";
        
        try
        {
            BufferedReader br = new BufferedReader ( new FileReader ( path ) );
            int ch = br.read(); //read single char
            
            if( ch == '0' )
            {
                ret = 0;
            }
            else if ( ch == '1' )
            {
                ret = 1;
            }
            
            br.close();
        }
        catch(IOException e){
            System.out.println( "GetGpioValue failed - " + gpio + ", path = " + path );
        }
        
        return ret;
    }
    
    private String GetGpioValueAsString( int gpio )
    {
        int value = GetGpioValue( gpio );
        
        if( value < 0 )
        {
            return "unknown";
        }
        else if( value == 0 )               // ACTIVE-LOW: SO WE ARE INVERTING HERE
        {
            // TODO: HERE AND IN CLIENT CHANGE "fire=true" or false to "fire=<timestamp>" or false.
            //       SAME for water and other simple alarms.

            return "true";                  // 0 = FLAME/WATER DETECTED = true string
        }
        else
        {
            return "false";                 // 1 = NO DETECTION = false string
        }
    }
    
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
    
    public static String QueryUploadURL( CloseableHttpClient httpclient, String GetNextUploadURL ) throws Exception
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
    
    public void run()
    {
        try
        {
            DateFormat df = new SimpleDateFormat("MM dd yyyy HH:mm:ss zzz");
            
            ExportGpio( FlameSensorGPIONumber );
            SetGpioDirection( FlameSensorGPIONumber, true );
            
            ExportGpio( WaterSensorGPIONumber );
            SetGpioDirection( WaterSensorGPIONumber, true );
            
            //while( true )
            for( int i = 0; i < 1; ++i )
            {
                if( _nextUploadURL == null || _nextUploadURL.isEmpty() )
                {
                    try
                    {
                        _nextUploadURL = QueryUploadURL( _httpclient, _GetNextUploadURL );
                    }
                    catch( Exception eUrl )
                    {
                        System.out.println("Exception getting next url: " + eUrl.toString());                    
                    }                
                }
                
                if( _nextUploadURL == null || _nextUploadURL.isEmpty() )
                {
                    try
                    {
                        Thread.sleep(5000);
                    }
                    catch( InterruptedException e )
                    {
                        System.out.println("Exception in sleep: " + e.toString());                
                    }
                    
                    // start loop again...
                    continue;
                }
                
                System.out.println("START OF LOOP UPLOADURL: '" + _nextUploadURL + "'");
                
                HttpPost httppost = new HttpPost( _nextUploadURL );
                
                Date now = new Date();
        
                httppost.setHeader("enctype", "multipart/form-data");
                
                String name          = "roverX";                // magic, form-data, name that our upload server looks for
                String contenttype   = "image/jpeg";            // mime-type
                File   imageFile     = new File( _imageFilename );
                String filename      = _blobstoreFilename;      // magic filename used by blobstore, which we need rover's to set to this strict
                                                                //   format "rover_<id>_cam<number>.jpg" due to search parameters used in search page.
                                                                //   e.g. "rover_1_cam1.jpg"
                                                                //
                                                                //   Theoretically, the <id> can be anything that doesn't contain underscore, but, for now,
                                                                //   it should be an ordinal value equivalent to a "serial number".
                                                                // The camera <number> should likewise be ordinal value
                                                                //   (which should be always 1 since, we are only installing 1 camera on each rover.)

                System.out.println("_imageFilename = " + _imageFilename + ", _blobstoreFilename = " + _blobstoreFilename );
                
                MultipartEntityBuilder meb = MultipartEntityBuilder.create();                                
                meb.addBinaryBody( name,
                                   imageFile,
                                   ContentType.create( contenttype ),
                                   filename );
                meb.addTextBody("name", "rover1");
                meb.addTextBody("date", df.format(now));
                if(false) {
                    meb.addTextBody("fire", GetGpioValueAsString(FlameSensorGPIONumber));                
                    meb.addTextBody("water", GetGpioValueAsString(WaterSensorGPIONumber));              // when on rover, query gpio
                } else {
                    meb.addTextBody("fire", "true");                
                    meb.addTextBody("water", "true");                    
                }
                HttpEntity reqEntity = meb.build();
    
                System.out.println("reqEntity = " + reqEntity.toString() );
                
                httppost.setEntity(reqEntity);
                
                CloseableHttpResponse response = null;
                
                try {
                    System.out.println("executing request " + httppost.getRequestLine());
                    response = _httpclient.execute(httppost);
                    
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
                        
                        if( nextURLPosition >= 0 ) {
                            String nextUploadURLandMore = entityString.substring( nextURLPosition + urlMarker.length() );
                            String[] splitLines = nextUploadURLandMore.split("[\\r\\n]+");
                            System.out.println("NEXT UPLOADURL: " + splitLines[0]);
                
                            // use this for next try
                            _nextUploadURL = splitLines[0].trim();
                            
                            WriteStringIntoFile( _nextUploadURL, UploadURLCacheFile );
                        } else {
                            System.out.println( "urlMarker (" + urlMarker + ") not found, so _nextUploadURL." );                            
                        }
                    }
                    
                    EntityUtils.consume(resEntity);
                }
                catch( Exception e )
                {
                    System.out.println("Exception posting image: " + e.toString());
                    
                    // try getting a new url
                    _nextUploadURL = null;                    
                }
                finally
                {
                    if( response != null )
                    {
                        try
                        {
                            response.close();
                        }
                        catch( IOException e )
                        {
                            System.out.println("Exception closing response: " + e.toString());            
                        }
                        response = null;
                    }
                }

                try
                {
                    Thread.sleep(5000);
                }
                catch( InterruptedException e )
                {
                    System.out.println("Exception in sleep: " + e.toString());                
                }
            } // end-for or while
        }
        finally
        {
            try
            {
                _httpclient.close();
            }
            catch( IOException e )
            {
                System.out.println("Exception closing _httpclient: " + e.toString());        
            }
        }

     }
    
    public static void main(String[] args) throws Exception
    {    
        if (args.length != 3)
        {
            System.out.println("GetNextUploadURL, File path, and blobstore filename not given");
            System.exit(1);
        }
        
        System.out.println("args[0]=" + args[0] + ", args[1]" + args[1] + ", args[2]" + args[2]);
        
        CloseableHttpClient httpclient = HttpClients.createDefault();
        
        //try
        {            
            String nextUploadURL = null;
            /*
            boolean startWithNewURL = true;
                        
            String urlFromFile = ReadFileIntoString( UploadURLCacheFile, Charset.defaultCharset() );            

            if( startWithNewURL || urlFromFile == null || urlFromFile.isEmpty())
            {
                nextUploadURL = QueryUploadURL( httpclient );
                System.out.println("START PROGRAM: GOT UPLOADURL FROM SERVER: '" + nextUploadURL + "'");
            }
            else
            {
                nextUploadURL = urlFromFile;
                System.out.println("START PROGRAM: GOT UPLOADURL FROM FILE: '" + nextUploadURL + "'");
            }
            */
            
            Thread thread = new Thread(new ClientMultipartFormPost( httpclient,
                                                                    args[0],            // GetNextUploadURL - url to get upload url
                                                                    nextUploadURL,
                                                                    args[1],            // image filename
                                                                    args[2]             // blobstorage magic filename (must be in format: rover_x_camN)
                                                                   ) );
            thread.start();
        }
        //catch( NoSuchFileException e )
        //{
        //    System.out.println("File not found: " + UploadURLCacheFile);        
        //}
    }    
}