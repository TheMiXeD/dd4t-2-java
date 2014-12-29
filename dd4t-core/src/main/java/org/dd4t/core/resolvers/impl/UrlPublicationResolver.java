package org.dd4t.core.resolvers.impl;

import org.apache.commons.lang3.StringUtils;
import org.dd4t.core.exceptions.SerializationException;
import org.dd4t.core.resolvers.PublicationResolver;
import org.dd4t.core.util.HttpUtils;
import org.dd4t.core.util.PublicationDescriptor;
import org.dd4t.providers.PageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileNotFoundException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Publication id for the current request by looking at the request URI. It uses a discovery phase where it
 * interrogates Hammerfest trying to lookup the Publication id by Publication URL property, and if not found, then by
 * Images URL (in case of binary URLs). The identified ids are stored in a concurrent hash map for the duration of the
 * web-application.
 * <p/>
 * Ther resolver uses property 'level' to indicate how many levels deep in the URI path to search for a valid
 * Publication URL or Images URL value.
 */
public class UrlPublicationResolver implements PublicationResolver {

	private static final Logger LOG = LoggerFactory.getLogger(UrlPublicationResolver.class);
	private static final ConcurrentMap<String, Integer> urlToIdMap = new ConcurrentHashMap<>();

	@Autowired
	private PageProvider pageProvider;

	private int level;
	private Pattern includePattern = null;


	/**
	 * Gets the Publication TCMURI item id for the current request
	 *
	 * @return int representing the SDL Tridion Publication item id
	 */
	@Override
	public int getPublicationId() {
		return getPublicationDescriptor().getId();
	}

	/**
	 * Gets the Publication URL property as defined in Tridion Publication properties corresponding to the current request
	 *
	 * @return String representing the SDL Tridion Publication URL property
	 */
	@Override
	public String getPublicationUrl() {
		return getPublicationDescriptor().getPublicationUrl();
	}

	/**
	 * Gets the Images URL property as defined in Tridion Publication properties corresponding to the current request
	 *
	 * @return String representing the SDL Tridion Images URL property
	 */
	@Override
	public String getImagesUrl() {
		return getPublicationDescriptor().getImageUrl();
	}

	/**
	 * Gets the Page URL in the current Publication corresponding to the given generic URL
	 *
	 * @param url String representing the generic URL (i.e. URL path without PublicationUrl prefix)
	 * @return String representing the current Publication URL followed by the given URL
	 */
	@Override
	public String getLocalPageUrl(String url) {
		String publicationUrl = getPublicationUrl();
		if (StringUtils.isNotEmpty(publicationUrl)) {
			return HttpUtils.normalizeUrl(publicationUrl + "/" + url);
		}
		return null;
	}

	/**
	 * Gets the Binary URL in the current Publication corresponding to the given generic URL
	 *
	 * @param url String representing the generic URL (i.e. URL path without PublicationUrl prefix)
	 * @return String representing the current Publication URL followed by the given URL
	 */
	@Override
	public String getLocalBinaryUrl(String url) {
		String imagesUrl = getImagesUrl();
		if (StringUtils.isNotEmpty(imagesUrl)) {
			return HttpUtils.normalizeUrl(imagesUrl + "/" + url);
		}
		return null;
	}

	/**
	 * Setter for level field.
	 *
	 * @param level
	 */
	public void setLevel(int level) {
		LOG.debug("Set level: {} ", level);
		this.level = level;
	}

	/**
	 * Setter for includePattern field.
	 *
	 * @param includePattern
	 */
	public void setIncludePattern(String includePattern) {
		this.includePattern = Pattern.compile(includePattern);
		LOG.debug("Set includePattern:  {}", includePattern);
	}


	/**
	 * Getter for includePattern field.
	 *
	 * @return includePattern
	 */
	public Pattern getIncludePattern() {
		if (includePattern == null) {
			String servletInitParam = HttpUtils.getCurrentServletContext().getInitParameter("includePattern");
			if (servletInitParam != null) {
				includePattern = Pattern.compile(servletInitParam);
				LOG.debug("Set includePattern:  {}", includePattern);
			}
		}
		return includePattern;
	}

	/**
	 * Setter for PageProvider field.
	 *
	 * @param provider
	 */
	public void setPageProvider(PageProvider provider) {
		LOG.debug("Set PageProvider: {} ", provider);
		this.pageProvider = provider;
	}

	/*
	Try resolve Publication id by looking up the Publication Url property in the Page Provider and,
	if not found by looking up the Images URL property in the Binary Provider
	 */
	private int discoverPublicationId(String urlStub) throws SerializationException {
		LOG.debug("Discover Publication id by UrlStub: {} ", urlStub);

		try {
			return pageProvider.discoverPublicationId(urlStub);
		} catch (SerializationException e) {
			LOG.error("Error while discovering Publication id by Url " + urlStub, e);
			throw e;
		}
	}

	private PublicationDescriptor getPublicationDescriptor() {
		Object pd = HttpUtils.currentRequest().getSession().getAttribute("publicationdescriptor");
		if (pd != null) {
			try {
				String baseUrl = getBaseUrl();
				if ((baseUrl.equals(((PublicationDescriptor)pd).getPublicationUrl()))) {
					return (PublicationDescriptor) pd;
				}
			} catch (FileNotFoundException e) {
				// TODO decide how to handle the situation where the current URL cannot be mapped to a publication
				// but there is already a publication descriptor in the session
				// for now, we will remove the pd from the session
				HttpUtils.currentRequest().getSession().removeAttribute("publicationdescriptor");
				return getEmptyPublicationDescriptor();
			}
		}

		try {
			PublicationDescriptor publicationDescriptor = createPublicationDescriptor();
			HttpUtils.currentRequest().getSession().setAttribute("publicationdescriptor", publicationDescriptor);
			return publicationDescriptor;
		} catch (FileNotFoundException e) {
			return getEmptyPublicationDescriptor(); // do not store this in the session!!
		}
	}

	private String getBaseUrl() throws FileNotFoundException {
		String urlPath = HttpUtils.getOriginalUri(HttpUtils.currentRequest());
		// if there is a context path, and the current URI starts with it, remove the context path
		if (urlPath.startsWith(HttpUtils.currentRequest().getContextPath())) {
			urlPath = urlPath.substring(HttpUtils.currentRequest().getContextPath().length());
		}
		// if url is empty or only a slash, we cannot find the publication
		if (StringUtils.isEmpty(urlPath) || urlPath.equals("/")) {
			throw new FileNotFoundException("No publication found for path " + urlPath);
		}
		return HttpUtils.createPathFromUri(urlPath, level);

	}

	private PublicationDescriptor createPublicationDescriptor() throws FileNotFoundException {
		PublicationDescriptor publicationDescriptor = new PublicationDescriptor();
		try {
			publicationDescriptor.setPublicationUrl(getBaseUrl());

			if (urlToIdMap.containsKey(publicationDescriptor.getPublicationUrl())) {
				publicationDescriptor.setId(urlToIdMap.get(publicationDescriptor.getPublicationUrl()));
			} else {
				// it is possible to define an include pattern
				// only if the stub matches this pattern, we will try to
				// resolve the publication
				Pattern includePattern = getIncludePattern();
				if (includePattern != null) {
					Matcher m = includePattern.matcher(publicationDescriptor.getPublicationUrl());
					if (!m.matches()) {
						publicationDescriptor.setId(-1);
						return publicationDescriptor;
					}
				}
				int pubId = discoverPublicationId(publicationDescriptor.getPublicationUrl());

				if (pubId > 0) {
					LOG.info("Found publication id {} for publication url {}", pubId, publicationDescriptor.getPublicationUrl());
					urlToIdMap.put(publicationDescriptor.getPublicationUrl(), pubId);
				}
				else {
					LOG.error("Publication resolver returned " + pubId + " for url: " + publicationDescriptor.getPublicationUrl());
				}

				publicationDescriptor.setId(pubId);
			}
			return publicationDescriptor;
		} catch (SerializationException e) {
			throw new RuntimeException(e);
		}
	}

	private PublicationDescriptor getEmptyPublicationDescriptor() {
		PublicationDescriptor pd = new PublicationDescriptor();
		pd.setId(-1);
		pd.setPublicationUrl("/");
		pd.setImageUrl("/");
		return pd;
	}
}