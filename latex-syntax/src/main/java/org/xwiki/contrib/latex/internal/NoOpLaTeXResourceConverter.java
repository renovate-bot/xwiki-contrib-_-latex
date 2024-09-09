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
package org.xwiki.contrib.latex.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;

/**
 * Provide a default converter that does nothing so that the LaTeX Syntax can be used even without using the Exporter
 * (in that case the resource references are not converted).
 *
 * @version $Id$
 */
@Component
@Named("noop")
@Singleton
public class NoOpLaTeXResourceConverter implements LaTeXResourceConverter
{
    @Override
    public void initialize(EntityReference currentReference, OutputStream outputStream)
    {
        // Nothing to do
    }

    @Override
    public ResourceReference convert(ResourceReference reference, String baseResourceReference, boolean forceDownload)
    {
        // No conversion
        return reference;
    }

    @Override
    public ResourceReference convert(ResourceReference reference, boolean forceDownload)
    {
        return reference;
    }

    @Override
    public ResourceReference convert(LinkBlock linkBlock)
    {
        return linkBlock.getReference();
    }

    @Override
    public ResourceReference convert(ImageBlock imageBlock)
    {
        return imageBlock.getReference();
    }

    @Override
    public void store(String path, InputStream inputStream) throws IOException
    {
        // Nothing to do
    }
}
