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
package org.xwiki.contrib.latex.internal.pdf;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.latex.pdf.LaTeX2PDFConfiguration;

/**
 * Default configuration options for converting from LaTeX to PDF.
 *
 * @version $Id$
 * @since 1.10
 */
@Component
@Singleton
public class DefaultLaTeX2PDFConfiguration implements LaTeX2PDFConfiguration
{
    private static final String PREFIX = "latex.pdf.";

    @Inject
    @Named("xwikiproperties")
    private ConfigurationSource configurationSource;

    @Override
    public String getDockerImageName()
    {
        return this.configurationSource.getProperty(PREFIX + "dockerImageName", "blang/latex:ubuntu");
    }

    @Override
    public List<String> getDockerCommands()
    {
        // Note: we run the compilation twice so that the TOC is generated properly (it requires two passes from
        // pdflatex).
        return Arrays.asList("sh", "-c", this.configurationSource.getProperty(PREFIX + "dockerCommands",
            "pdflatex -shell-escape index.tex; pdflatex -shell-escape index.tex"));
    }
}
