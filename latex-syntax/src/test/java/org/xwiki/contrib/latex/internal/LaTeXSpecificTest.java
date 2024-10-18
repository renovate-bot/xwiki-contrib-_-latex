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

import org.junit.runner.RunWith;
import org.xwiki.rendering.test.MockWikiModel;
import org.xwiki.rendering.test.integration.RenderingTestSuite;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.mockito.Mockito.when;

/**
 * Run all specific tests found in {@code *.test} files located in the classpath. These {@code *.test} files must follow
 * the conventions described in {@link org.xwiki.rendering.test.integration.TestDataParser}.
 *
 * @version $Id: a4b49961bb701fdc376e677e0358fbe16ba1c1ee $
 */
@RunWith(RenderingTestSuite.class)
@RenderingTestSuite.Scope(value = "latex10.specific")
@AllComponents
public class LaTeXSpecificTest
{
    @RenderingTestSuite.Initialized
    public void initialize(MockitoComponentManager componentManager) throws Exception
    {
        MockSetup.setUp(componentManager);

        componentManager.registerComponent(MockWikiModel.getComponentDescriptor());
        componentManager.registerMockComponent(SkinExtension.class, "ssfx");

        // Setup for the Raw macro execution
        ContextualAuthorizationManager contextualAuthorizationManager =
            componentManager.registerMockComponent(ContextualAuthorizationManager.class);
        when(contextualAuthorizationManager.hasAccess(Right.SCRIPT)).thenReturn(true);
    }
}
