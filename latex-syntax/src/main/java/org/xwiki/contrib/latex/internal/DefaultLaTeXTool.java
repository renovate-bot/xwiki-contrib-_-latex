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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.localization.LocalizationContext;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FigureBlock;
import org.xwiki.rendering.block.IdBlock;
import org.xwiki.rendering.block.MacroMarkerBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableHeadCellBlock;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.reference.link.LinkLabelGenerator;
import org.xwiki.script.ScriptContextManager;

/**
 * Provides useful tools for use in the LaTeX templates.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultLaTeXTool implements LaTeXTool, Initializable
{
    private static final String BACKSLASH = "\\";

    private static final String BEGINCURLY = "{";

    private static final String ENDCURLY = "}";

    private static final String HASH = "#";

    private static final String DOLLAR = "$";

    private static final String PERCENT = "%";

    private static final String TILDE = "~";

    private static final String[] SEARCH_STRINGS =
        { BACKSLASH, BEGINCURLY, ENDCURLY, HASH, DOLLAR, PERCENT, "&", "^", "_", TILDE };

    private static final String[] REPLACE_STRINGS =
        { "\\textbackslash{}", "\\{", "\\}", "\\#", "\\$", "\\%", "\\&", "\\^{}", "\\_", "\\~{}" };

    private static final String[] LABEL_INVALID_STRINGS =
        { BACKSLASH, BEGINCURLY, ENDCURLY, HASH, DOLLAR, PERCENT, TILDE };

    @Inject
    private LocalizationContext localizationContext;

    @Inject
    private ScriptContextManager scriptContextManager;

    @Inject
    private FigureTool figureTool;

    @Inject
    private IdBlockManager idBlockManager;

    /**
     * A parser that knows how to parse plain text; this is used to transform link labels into plain text.
     *
     * @since 1.17
     */
    @Inject
    @Named("plain/1.0")
    private Parser plainTextParser;

    /**
     * Generate link label.
     *
     * @since 1.17
     */
    @Inject
    private LinkLabelGenerator linkLabelGenerator;

    /**
     * Plain text filter to extract text content of, e.g., figure captions.
     *
     * @since 1.17
     */
    private LaTeXPlainTextBlockFilter plainTextBlockFilter;

    @Override
    public void initialize() throws InitializationException
    {
        this.plainTextBlockFilter = new LaTeXPlainTextBlockFilter(this.plainTextParser, this.linkLabelGenerator);
    }

    @Override
    public String escape(String input)
    {
        return StringUtils.replaceEach(input, SEARCH_STRINGS, REPLACE_STRINGS);
    }

    @Override
    public String normalizeLabel(String input)
    {
        String result = input;

        // Remove invalid characters
        for (String invalid : LABEL_INVALID_STRINGS) {
            result = StringUtils.remove(result, invalid);
        }
        return result;
    }

    @Override
    public String getLanguage()
    {
        return this.localizationContext.getCurrentLocale().getDisplayLanguage(Locale.ENGLISH).toLowerCase();
    }

    @Override
    public List<Block> getSiblings(Block currentBlock)
    {
        List<Block> results = new ArrayList<>();
        Block block = currentBlock.getNextSibling();
        while (block != null) {
            results.add(block);
            block = block.getNextSibling();
        }
        return results;
    }

    @Override
    public Stack<?> getStack(String id)
    {
        Stack<?> result;
        Map<String, Object> latexBinding = (Map<String, Object>) this.scriptContextManager.getCurrentScriptContext()
            .getAttribute(DefaultTemplateRenderer.SC_LATEX);
        result = (Stack<?>) latexBinding.get(id);
        if (result == null) {
            result = new Stack<>();
            latexBinding.put(id, result);
        }
        return result;
    }

    @Override
    public FigureTool getFigureTool()
    {
        return this.figureTool;
    }

    @Override
    public boolean isTableCell(Block block)
    {
        return (block instanceof TableCellBlock) || (block instanceof TableHeadCellBlock);
    }

    @Override
    public boolean isFigure(Block block)
    {
        return (block instanceof FigureBlock);
    }

    @Override
    public Block getParentBlock(Block current)
    {
        Block cursor = current;
        while ((cursor = cursor.getParent()) != null) {
            if (!(cursor instanceof MacroMarkerBlock)) {
                return cursor;
            }
        }
        return null;
    }

    @Override
    public boolean previousSiblingsContainsOnlyIdMacros(Block block)
    {
        boolean result = true;
        Block cursor = block.getPreviousSibling();
        while (cursor != null) {
            if (cursor instanceof MacroMarkerBlock && cursor.getChildren().size() == 1
                && cursor.getChildren().get(0) instanceof IdBlock)
            {
                cursor = cursor.getPreviousSibling();
            } else {
                result = false;
                break;
            }
        }
        return result;
    }

    @Override
    public boolean isIdBlockInline(IdBlock idBLock)
    {
        return this.idBlockManager.isInline(idBLock);
    }

    @Override
    public List<Block> getPlainTextDescendants(Block block)
    {
        return this.plainTextBlockFilter.getPlainTextDescendants(block);
    }

    @Override
    public Block getDescendantMetaDataBlockWithParameterName(Block currentBlock, String parameterName)
    {
        Block firstBlock = currentBlock.getFirstBlock(block -> MetaDataBlock.class.isAssignableFrom(block.getClass())
            && parameterName.equals(((MetaDataBlock) block).getMetaData().getMetaData(MetaData.PARAMETER_NAME)),
            Block.Axes.DESCENDANT);
        return firstBlock;
    }
}
