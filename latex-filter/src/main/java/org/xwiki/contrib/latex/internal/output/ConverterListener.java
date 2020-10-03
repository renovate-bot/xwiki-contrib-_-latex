/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.latex.internal.output;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.latex.output.LaTeXOutputProperties;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.listener.WrappingListener;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * @version $Id$
 */
@Component(roles = ConverterListener.class)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class ConverterListener extends WrappingListener
{
    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private EntityReferenceResolver<ResourceReference> resolver;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<EntityReference> currentDocumentResolver;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentStringDocumentResolver;

    @Inject
    @Named("latexpath")
    private EntityReferenceSerializer<String> latexPathSerializer;

    @Inject
    private DocumentAccessBridge bridge;

    @Inject
    private Logger logger;

    private Set<String> stored = new HashSet<>();

    private Deque<ResourceReference> currentReference = new ArrayDeque<>();

    private LaTeXOutputProperties properties;

    private ZipArchiveOutputStream zipStream;

    private EntityReference baseEntityReference;

    private DocumentReference currentDocumentReference;

    private Deque<String> baseResourceMetadataQueue = new ArrayDeque<>();

    /**
     * @param properties the filter properties
     * @param zipStream the zip to add entries to
     */
    public void initialize(LaTeXOutputProperties properties, ZipArchiveOutputStream zipStream)
    {
        this.zipStream = zipStream;
        this.properties = properties;
    }

    /**
     * @param entityReference the current reference
     */
    public void setCurrentReference(EntityReference entityReference)
    {
        this.baseEntityReference = entityReference;
        this.currentDocumentReference = null;
    }

    private ResourceReference convertReference(ResourceReference reference, boolean forceDownload)
    {
        ResourceReference convertedReference = reference;

        try {
            // TODO: make all this extensible instead of if/else
            if (ResourceType.ATTACHMENT.equals(reference.getType())) {
                convertedReference = convertATTACHMENTReference(convertedReference);
            } else if (ResourceType.DATA.equals(reference.getType())) {
                convertedReference = convertDATAReference(convertedReference);
            } else if (ResourceType.URL.equals(reference.getType())) {
                convertedReference = convertURLReference(convertedReference, forceDownload);
            } else if (ResourceType.DOCUMENT.equals(reference.getType())) {
                convertedReference = convertDOCUMENTReference(convertedReference);
            } else if (ResourceType.PATH.equals(reference.getType())) {
                convertedReference = convertPATHReference(convertedReference);
            }
        } catch (Exception e) {
            this.logger.error("Unexpected error when trying to convert resource reference [{}]", reference, e);
        }

        return convertedReference;
    }

    private ResourceReference convertPATHReference(ResourceReference reference)
    {
        // If the path doesn't start with "/" then consider that there's nothing to do: it's either already a URL
        // or it's something weird that we can't convert.
        String path = reference.getReference();
        if (!path.startsWith("/")) {
            return reference;
        }

        // Convert to an absolute URL.
        ResourceReference convertedResourceReference;
        try {
            XWikiContext xcontext = this.xcontextProvider.get();
            URL serverURL = xcontext.getWiki().getServerURL(
                this.baseEntityReference.extractReference(EntityType.WIKI).getName(), xcontext);
            convertedResourceReference = toURLReference(reference, new URL(serverURL, path).toString());
        } catch (MalformedURLException e) {
            // Error, log a warning and fall back to not doing any conversion
            this.logger.warn("Failed to convert path [{}] to a URL. Root error: [{}]", path,
                ExceptionUtils.getRootCauseMessage(e));
            convertedResourceReference = reference;
        }

        return convertedResourceReference;
    }

    private ResourceReference convertATTACHMENTReference(ResourceReference reference)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("files/attachments/");
        // Convert reference
        AttachmentReference attachmentReference =
            (AttachmentReference) this.resolver.resolve(reference, EntityType.ATTACHMENT, getBaseReference());

        String normalizedPath = this.latexPathSerializer.serialize(attachmentReference);
        builder.append(normalizedPath);

        String path = builder.toString();

        ResourceReference convertedReference = toReference(reference, path);

        // Store attachment content
        if (!this.stored.contains(path)) {
            try {
                XWikiContext xcontext = this.xcontextProvider.get();
                XWikiDocument document =
                    xcontext.getWiki().getDocument(attachmentReference.getDocumentReference(), xcontext);
                XWikiAttachment attachment = document.getAttachment(attachmentReference.getName());
                if (attachment != null) {
                    try (InputStream inputStream = attachment.getContentInputStream(xcontext)) {
                        store(path, inputStream);
                    }
                } else {
                    this.logger.warn("Can't find attachment with reference [{}]", attachmentReference);
                }
            } catch (Exception e) {
                this.logger.error("Failed to store attachment [{}]", attachmentReference, e);
            }
        }

        return convertedReference;
    }

    private ResourceReference convertDATAReference(ResourceReference reference)
    {
        // TODO

        return reference;
    }

    private ResourceReference convertURLReference(ResourceReference reference, boolean forceDownload)
    {
        ResourceReference convertedReference = reference;

        if (forceDownload) {
            // Create a local path for the URL resource

            // Download the file and store it in the zip
            try {
                URL url = new URL(reference.getReference());

                String filename = FilenameUtils.getName(url.toString());
                // Limit collision with same filename and domain but different URL
                String path =
                    "files/downloaded/" + url.getHost() + '/' + reference.getReference().hashCode() + '/' + filename;

                // Convert the reference
                convertedReference = toReference(reference, path);

                if (!this.stored.contains(path)) {
                    try (InputStream stream = url.openStream()) {
                        store(path, stream);
                    }
                }
            } catch (Exception e) {
                this.logger.error("Failed to download file with URL [{}]", reference.getReference(), e);
            }
        }

        return convertedReference;
    }

    private ResourceReference convertDOCUMENTReference(ResourceReference reference)
    {
        ResourceReference convertedReference = reference;

        if (StringUtils.isNotEmpty(reference.getReference())) {
            DocumentReference documentReference =
                (DocumentReference) this.resolver.resolve(reference, EntityType.DOCUMENT, getBaseReference());

            if (isCurrentDocument(documentReference)) {
                convertedReference = new DocumentResourceReference("");
                convertedReference.setParameters(reference.getParameters());
            } else {
                if (this.properties.getEntities() != null && this.properties.getEntities().matches(documentReference)) {
                    // TODO: ?
                } else {
                    // External URL
                    String url = this.bridge.getDocumentURL(new DocumentReference(documentReference), "view",
                        reference.getParameter(DocumentResourceReference.QUERY_STRING),
                        reference.getParameter(DocumentResourceReference.ANCHOR), true);

                    convertedReference = new ResourceReference(url, ResourceType.URL);
                }
            }
        }

        return convertedReference;
    }

    private DocumentReference getCurrentDocumentReference()
    {
        if (this.currentDocumentReference == null) {
            this.currentDocumentReference = this.currentDocumentResolver.resolve(this.baseEntityReference);
        }

        return this.currentDocumentReference;
    }

    private boolean isCurrentDocument(DocumentReference documentReference)
    {
        return getCurrentDocumentReference().equals(documentReference);
    }

    private ResourceReference toReference(ResourceReference reference, String path)
    {
        ResourceReference convertedReference = new ResourceReference(path, reference.getType());
        convertedReference.setParameters(reference.getParameters());
        return convertedReference;
    }

    private ResourceReference toURLReference(ResourceReference reference, String url)
    {
        ResourceReference convertedReference = new ResourceReference(url, ResourceType.URL);
        convertedReference.setParameters(reference.getParameters());
        return convertedReference;
    }

    private void store(String path, InputStream inputStream) throws IOException
    {
        ZipArchiveEntry entry = new ZipArchiveEntry(path);

        try {
            this.zipStream.putArchiveEntry(entry);

            IOUtils.copy(inputStream, this.zipStream);

            this.stored.add(path);
        } catch (IOException e) {
            this.logger.error("Failed to store file at [{}]", path, e);
        } finally {
            this.zipStream.closeArchiveEntry();
        }
    }

    @Override
    public void beginMetaData(MetaData metadata)
    {
        // Stack the BASE reference since we need it to resolve references
        String baseReference = getBaseReference(metadata);
        if (baseReference != null) {
            this.baseResourceMetadataQueue.push(baseReference);
        }
    }

    @Override
    public void endMetaData(MetaData metadata)
    {
        String baseReference = getBaseReference(metadata);
        if (baseReference != null) {
            this.baseResourceMetadataQueue.pop();
        }
    }

    private String getBaseReference(MetaData metadata)
    {
        String result = null;
        Map<String, Object> metaData = metadata.getMetaData();
        if (metaData != null) {
            result = (String) metaData.get(MetaData.BASE);
        }
        return result;
    }

    private EntityReference getBaseReference()
    {
        EntityReference reference;
        if (!this.baseResourceMetadataQueue.isEmpty()) {
            reference = this.currentStringDocumentResolver.resolve(this.baseResourceMetadataQueue.peek());
        } else {
            reference = this.baseEntityReference;
        }
        return reference;
    }

    @Override
    public void onImage(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        super.onImage(convertReference(reference, true), freestanding, parameters);
    }

    @Override
    public void beginLink(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        ResourceReference convertedReference = convertReference(reference, false);

        this.currentReference.push(convertedReference);

        super.beginLink(convertedReference, freestanding, parameters);
    }

    @Override
    public void endLink(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        super.endLink(this.currentReference.pop(), freestanding, parameters);
    }
}
