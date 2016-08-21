/**
 * Mupen64PlusAE, an N64 emulator for the Android platform
 * 
 * Copyright (C) 2013 Paul Lamb
 * 
 * This file is part of Mupen64PlusAE.
 * 
 * Mupen64PlusAE is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Mupen64PlusAE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Mupen64PlusAE. If
 * not, see <http://www.gnu.org/licenses/>.
 * 
 * Authors: littleguy77
 * 
 * References:
 * http://stackoverflow.com/questions/4447477/android-how-to-copy-files-in-assets-to-sdcard
 */
package paulscode.android.mupen64plusae.task;

import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import paulscode.android.mupen64plusae.util.FileUtil;

public class ExtractAssetsTask extends AsyncTask<Void, String, List<ExtractAssetsTask.Failure>>
{
    public interface ExtractAssetsListener
    {
        public void onExtractAssetsProgress( String nextFileToExtract );
        public void onExtractAssetsFinished( List<Failure> failures );
    }
    
    public ExtractAssetsTask( AssetManager assetManager, String srcPath, String dstPath, ExtractAssetsListener listener )
    {
        if (assetManager == null )
            throw new IllegalArgumentException( "Asset manager cannot be null" );
        if( TextUtils.isEmpty( srcPath ) )
            throw new IllegalArgumentException( "Source path cannot be null or empty" );
        if( TextUtils.isEmpty( dstPath ) )
            throw new IllegalArgumentException( "Destination path cannot be null or empty" );
        if( listener == null )
            throw new IllegalArgumentException( "Listener cannot be null" );
        
        mAssetManager = assetManager;
        mSrcPath = srcPath;
        mDstPath = dstPath;
        mListener = listener;
    }
    
    private final AssetManager mAssetManager;
    private final String mSrcPath;
    private final String mDstPath;
    private final ExtractAssetsListener mListener;
    
    @Override
    protected List<Failure> doInBackground( Void... params )
    {
        return extractAssets( mSrcPath, mDstPath );
    }
    
    @Override
    protected void onProgressUpdate( String... values )
    {
        mListener.onExtractAssetsProgress( values[0] );
    }
    
    @Override
    protected void onPostExecute( List<ExtractAssetsTask.Failure> result )
    {
        mListener.onExtractAssetsFinished( result );
    }
    
    public static final class Failure
    {
        public enum Reason
        {
            FILE_UNWRITABLE,
            FILE_UNCLOSABLE,
            ASSET_UNCLOSABLE,
            ASSET_IO_EXCEPTION,
            FILE_IO_EXCEPTION,
        }
        
        public final String srcPath;
        public final String dstPath;
        public final Reason reason;
        public Failure( String srcPath, String dstPath, Reason reason )
        {
            this.srcPath = srcPath;
            this.dstPath = dstPath;
            this.reason = reason;
        }
        
        @Override
        public String toString()
        {
            switch( reason )
            {
                case FILE_UNWRITABLE:
                    return "Failed to open file " + dstPath;
                case FILE_UNCLOSABLE:
                    return "Failed to close file " + dstPath;
                case ASSET_UNCLOSABLE:
                    return "Failed to close asset " + srcPath;
                case ASSET_IO_EXCEPTION:
                    return "Failed to extract asset " + srcPath + " to file " + dstPath;
                case FILE_IO_EXCEPTION:
                    return "Failed to add file " + srcPath + " to file " + dstPath;
                default:
                    return "Failed using source " + srcPath + " and destination " + dstPath;
            }
        }
    }
    
    private List<Failure> extractAssets( String srcPath, String dstPath )
    {
        final List<Failure> failures = new ArrayList<Failure>();
        
        if( srcPath.startsWith( "/" ) )
            srcPath = srcPath.substring( 1 );
        
        String[] srcSubPaths = getAssetList( mAssetManager, srcPath );
        
        if( srcSubPaths.length > 0 )
        {
            // srcPath is a directory
            
            // Ensure the parent directories exist
            FileUtil.makeDirs(dstPath);
            
            // Recurse into each subdirectory
            for( String srcSubPath : srcSubPaths )
            {
                String suffix = "/" + srcSubPath;
                failures.addAll( extractAssets( srcPath + suffix, dstPath + suffix ) );
            }
        }
        else // srcPath is a file.
        {
            // Call the progress listener before extracting
            publishProgress( dstPath );
            
            // IO objects, initialize null to eliminate lint error
            OutputStream out = null;
            InputStream in = null;
            
            // Extract the file
            try
            {
                out = new FileOutputStream( dstPath );
                in = mAssetManager.open( srcPath );
                byte[] buffer = new byte[1024];
                int read;
                
                while( ( read = in.read( buffer ) ) != -1 )
                {
                    out.write( buffer, 0, read );
                }
                out.flush();
            }
            catch( FileNotFoundException e )
            {
                Failure failure = new Failure( srcPath, dstPath, Failure.Reason.FILE_UNWRITABLE ); 
                Log.e( "ExtractAssetsTask", failure.toString() );
                failures.add( failure );
            }
            catch( IOException e )
            {
                Failure failure = new Failure( srcPath, dstPath, Failure.Reason.ASSET_IO_EXCEPTION ); 
                Log.e( "ExtractAssetsTask", failure.toString() );
                failures.add( failure );
            }
            finally
            {
                if( out != null )
                {
                    try
                    {
                        out.close();
                    }
                    catch( IOException e )
                    {
                        Failure failure = new Failure( srcPath, dstPath, Failure.Reason.FILE_UNCLOSABLE ); 
                        Log.e( "ExtractAssetsTask", failure.toString() );
                        failures.add( failure );
                    }
                }
                if( in != null )
                {
                    try
                    {
                        in.close();
                    }
                    catch( IOException e )
                    {
                        Failure failure = new Failure( srcPath, dstPath, Failure.Reason.ASSET_UNCLOSABLE ); 
                        Log.e( "ExtractAssetsTask", failure.toString() );
                        failures.add( failure );
                    }
                }
            }
        }
        
        return failures;
    }
    
    private static String[] getAssetList( AssetManager assetManager, String srcPath )
    {
        String[] srcSubPaths = null;
        
        try
        {
            srcSubPaths = assetManager.list( srcPath );
        }
        catch( IOException e )
        {
            Log.w( "ExtractAssetsTask", "Failed to get asset file list." );
        }
        
        return srcSubPaths;
    }
}
