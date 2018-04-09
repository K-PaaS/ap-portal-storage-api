package org.openpaas.paasta.portal.storage.api.store.swift;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.javaswift.joss.model.Container;
import org.javaswift.joss.model.StoredObject;
import org.openpaas.paasta.portal.storage.api.config.SwiftOSConstants.SwiftOSCommonParameter;
import org.openpaas.paasta.portal.storage.api.store.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SwiftOSService extends ObjectStorageService<SwiftOSFileInfo> {
    private static final Logger LOGGER = LoggerFactory.getLogger( SwiftOSService.class );
    
    @Autowired
    private final Container container;
    
    public SwiftOSService(Container container) {
        this.container = container;
    }
    
    @Override
    public SwiftOSFileInfo putObject( final MultipartFile multipartFile ) throws IOException {
        Assert.notNull( multipartFile, "MultipartFile instance is empty : " + multipartFile );
        
        final String filename = multipartFile.getOriginalFilename();
        final String storedFilename = generateStoredFilename( filename );
        LOGGER.debug( "transferring filename : {}", filename );
        LOGGER.debug( "storing filename into object storage : {}", storedFilename );
        
        // create StoredObject instance
        final StoredObject object = container.getObject( storedFilename );
        LOGGER.debug( "StoredObject : {}", object );
        
        // upload object
        object.uploadObject( multipartFile.getInputStream() );
        LOGGER.debug( "Done upload object : {} ({})", storedFilename, object.getPublicURL() );
        
        // after it uploads object, it sets content type and additional metadata in object storage
        object.setContentType( multipartFile.getContentType() );
        object.setAndSaveMetadata( SwiftOSCommonParameter.OBJECT_ORIGINAL_FILENAME_METAKEY, multipartFile.getOriginalFilename() );
        
        final SwiftOSFileInfo fileInfo = SwiftOSFileInfo.newInstanceFromStoredObject( object );
        LOGGER.debug( "SwiftOSFileInfo : {}", fileInfo );
        
        return fileInfo;
    }
    
    public SwiftOSFileInfo putObject( final String filename, final InputStream content, final String contentType ) {
        Assert.notNull( filename, "Filename instance is empty : " + filename );
        Assert.notNull( content, "InputStream content instance is empty : " + content );
        Assert.notNull( contentType, "Content type instance is empty : " + contentType );
        
        // create StoredObject instance
        final String storedFilename = generateStoredFilename( filename );
        final StoredObject object = container.getObject( storedFilename );
        LOGGER.debug( "StoredObject : {}", object );
        
        // upload object
        object.uploadObject( content );
        LOGGER.debug( "Done upload object : {} ({})", storedFilename, object.getPublicURL() );
        
        // after its service uploads object(content), it sets content type and additional metadata in object storage
        object.setContentType( contentType );
        object.setAndSaveMetadata( SwiftOSCommonParameter.OBJECT_ORIGINAL_FILENAME_METAKEY, filename );
        
        final SwiftOSFileInfo fileInfo = SwiftOSFileInfo.newInstanceFromStoredObject( object );
        LOGGER.debug( "SwiftOSFileInfo : {}", fileInfo );
        
        return fileInfo;
    }
    
    @Override
    public SwiftOSFileInfo getObject( final String filename ) throws FileNotFoundException {
        Assert.notNull( filename, "Filename instance is empty : " + filename );
        
        final StoredObject object = getRawObject( filename );
        final SwiftOSFileInfo fileInfo = SwiftOSFileInfo.newInstanceFromStoredObject( object );
        LOGGER.debug( "SwiftOSFileInfo : {}", fileInfo );
        
        return fileInfo;
    }
    
    public StoredObject getRawObject( final String filename ) {
        Assert.notNull( filename, "Filename instance is empty : " + filename );
        
        final StoredObject object = container.getObject( filename );
        if (false == object.exists()) 
            return null;
        else
            return object;
    }

    @Override
    public SwiftOSFileInfo updateObject( String filename, MultipartFile multipartFile ) {
        throw new UnsupportedOperationException("Updating object doesn't support yet.");
    }

    @Override
    public boolean removeObject( final String filename ) {
        Assert.notNull( filename, "Filename instance is empty : " + filename );
        
        // reload before delete object
        container.reload();
        
        final StoredObject object = container.getObject( filename );
        Assert.notNull( object, "StoredObject instance is empty : " + object );
        
        if (true == object.exists()) {
            LOGGER.debug( "Delete object : {} ({})", object.getName(), object.getMetadata( SwiftOSCommonParameter.OBJECT_ORIGINAL_FILENAME_METAKEY ) );
            object.delete();
        } else {
            LOGGER.warn( "Cannot delete non-existed object... : {}", filename );
        }
        
        // after delete...
        if (true == object.exists()) {
            Exception ex = new IllegalStateException( "File(" + filename + ") can't delete..." );
            LOGGER.error( "Cannot delete...", ex );
            
            return false;
        }
        
        return true;
    }
    
    public List<String> listFileURLs() {
        final Collection<StoredObject> list = container.list();
        Assert.notNull( list, "StoredObject list is empty : " + list );
        
        final List<String> urlList = new ArrayList<>();
        for (StoredObject object : list)
            urlList.add( object.getName() + " ( <a>" + object.getPublicURL() + "</a> )" );
        
        return urlList;
    }

    protected final String generateStoredFilename( final String filename ) {
        Assert.notNull( filename, "Filename instance is empty : " + filename );
        
        // Filename Rule : [uuid-32-chars]-[original-file-name]
        // example : 081e756fd63f4648b077a42cc4acf88e-site_logo.png
        final StringBuffer buffer = new StringBuffer(UUID.randomUUID().toString().replaceAll( "-", "" ));
        buffer.append( '-' ).append( filename );
        
        return buffer.toString(); 
    }
    
    protected final String getOriginalFilename( final String storedFilename ) {
        Assert.notNull( storedFilename, "Stored object's filename instance is empty : " + storedFilename );
        
        // recycle method
        return SwiftOSFileInfo.getOriginalFilename( storedFilename );
    }
}