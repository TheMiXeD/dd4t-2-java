/*
 * Copyright (c) 2015 SDL, Radagio & R. Oudshoorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dd4t.mvc.utils;

import org.dd4t.core.factories.PublicationResolverFactory;
import org.dd4t.core.resolvers.PublicationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PublicationResolverFactoryImpl hides actual implementation
 * Configured through Spring beans
 *
 * This is needed for allowing static access in PublicationUrl.java,
 * an EL function.
 *
 * @author Mihai Cadariu
 */
public class PublicationResolverFactoryImpl implements PublicationResolverFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationResolverFactoryImpl.class);
    private static final PublicationResolverFactory INSTANCE = new PublicationResolverFactoryImpl();

	private PublicationResolver publicationResolver;

    private PublicationResolverFactoryImpl() {
        LOG.debug("Create new instance");
    }

    public static PublicationResolverFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public PublicationResolver getPublicationResolver() {
        return publicationResolver;
    }

    public void setPublicationResolver(PublicationResolver publicationResolver) {
        LOG.debug("Set PublicationResolver " + publicationResolver);
        this.publicationResolver = publicationResolver;
    }
}
