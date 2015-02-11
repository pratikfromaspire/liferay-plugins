/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.shortlink.service.impl;

import com.liferay.portal.kernel.dao.orm.ORMException;
import com.liferay.portal.kernel.dao.orm.QueryPos;
import com.liferay.portal.kernel.dao.orm.SQLQuery;
import com.liferay.portal.kernel.dao.orm.Session;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.transaction.Isolation;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.Transactional;
import com.liferay.portal.kernel.util.CalendarUtil;
import com.liferay.shortlink.NoSuchEntryException;
import com.liferay.shortlink.ShortURLTakenException;
import com.liferay.shortlink.model.ShortLinkEntry;
import com.liferay.shortlink.service.base.ShortLinkEntryLocalServiceBaseImpl;
import com.liferay.shortlink.util.ShortURLUtil;
import com.liferay.util.dao.orm.CustomSQLUtil;

import java.sql.Timestamp;

import java.util.Date;
import java.util.List;

import static com.liferay.shortlink.util.ApplicationConstants.AUTO_SHORTEN_PREFIX;

/**
 * The implementation of the link local service. <p/>
 *
 * <p>
 * All custom service methods should be put in this class. Whenever methods are
 * added, rerun ServiceBuilder to copy their definitions into the {@link
 * com.liferay.shortlink.service.ShortLinkEntryLocalService} interface. <p/> <p> This
 * is a local service. Methods of this service will not have security checks
 * based on the propagated JAAS credentials because this service can only be
 * accessed from within the same VM.
 * </p>
 *
 * @author Miroslav Ligas
 * @see    com.liferay.shortlink.service.base.ShortLinkEntryLocalServiceBaseImpl
 * @see    com.liferay.shortlink.service.ShortLinkEntryLocalServiceUtil
 */
public class ShortLinkEntryLocalServiceImpl
	extends ShortLinkEntryLocalServiceBaseImpl {

	/**
	 * Method checks if the short link is already used. If the link is used an
	 * Exception will be thrown. If the link is unused the original link will be
	 * checked if it is already in the database. If the link is found, it will
	 * be returned otherwise a new link will be stored to database and all
	 * appropriate model listeners will be notified.
	 *
	 * @param  originalURL original URL to be shortened
	 * @return the link that was added
	 * @throws com.liferay.shortlink.ShortURLTakenException if the short
	 *         link is already taken
	 */
	public ShortLinkEntry addAutogeneratedShortLinkEntry(String originalURL)
		throws ShortURLTakenException, SystemException {

		List<ShortLinkEntry> links = shortLinkEntryPersistence.findByOURL_A(
			originalURL, true);

		ShortLinkEntry result;

		if (links.isEmpty()) {
			ShortLinkEntry newLink = createNewShortLinkEntry(
				true, originalURL, null);

			String shortLink =
				AUTO_SHORTEN_PREFIX + ShortURLUtil.encode(
					newLink.getShortLinkEntryId());

			if (isShortURLNotUnique(shortLink)) {
				throw new ShortURLTakenException(
					"ShortLinkEntry '" + shortLink + "' is not unique");
			}
			else {
				newLink.setShortURL(shortLink);
				result = super.addShortLinkEntry(newLink);
			}
		}
		else {
			result = links.get(0);
		}

		return result;
	}

	/**
	 * Method checks if the short link is already used. If the link is used an
	 * Exception will be thrown. If the link is unused the original link will be
	 * checked if it is already in the database. If the link is found, it will
	 * be returned otherwise a new link will be stored to database and all
	 * appropriate model listeners will be notified.
	 *
	 * @param  originalURL original URL
	 * @param  shortURL short substitute of the original URL
	 * @return the link that was added
	 * @throws com.liferay.shortlink.ShortURLTakenException if the short
	 *         link is already taken
	 */
	public ShortLinkEntry addShortLinkEntry(
			String originalURL, String shortURL)
		throws ShortURLTakenException, SystemException {

		if (isShortURLNotUnique(shortURL)) {
			throw new ShortURLTakenException(
				"ShortLinkEntry '" + shortURL + "' is not unique");
		}

		return createNewShortLinkEntry(false, shortURL, originalURL);
	}

	/**
	 * Deletes all the Links that were not modified after the specified date.
	 *
	 * @param olderThen boundary date for the deletion.
	 */
	@Override
	@Transactional(
		isolation = Isolation.READ_COMMITTED,
		propagation = Propagation.REQUIRES_NEW)
	public void deleteOldRecords(Date olderThen) {

		try {
			Session session = shortLinkEntryPersistence.openSession();

			String sql = CustomSQLUtil.get(_DELETE_LINKS);

			SQLQuery sqlQuery = session.createSQLQuery(sql);

			QueryPos qPos = QueryPos.getInstance(sqlQuery);

			Timestamp olderThenTS = CalendarUtil.getTimestamp(olderThen);

			qPos.add(olderThenTS);

			sqlQuery.executeUpdate();

			shortLinkEntryPersistence.closeSession(session);

		}
		catch (ORMException orme) {
			_LOG.error("Unable to remove old Links.", orme);
		}
	}

	/**
	 * Method loads links auto-generated or explicitly created links from
	 * database. Method supports paging.
	 *
	 * @param  autogenerated specifies what kind of links should be loaded
	 * @param  start the lower bound of the range of links
	 * @param  end the upper bound of the range of links (not inclusive)
	 * @return the range of matching links
	 * @throws SystemException if a system exception occurred
	 */
	@Override
	public List<ShortLinkEntry> getShortLinkEntryByAutogenerated(
			boolean autogenerated, int start, int end)
		throws SystemException {

		return shortLinkEntryPersistence.findByAutogenerated(
			autogenerated, start, end);
	}

	/**
	 * Method returns the link which short link matches the provided value.
	 *
	 * @param  shortURL the short link
	 * @return the matching link
	 * @throws com.liferay.shortlink.NoSuchEntryException if a matching link
	 *         could not be found
	 * @throws SystemException if a system exception occurred
	 */
	@Override
	public ShortLinkEntry getShortLinkEntryByShortURL(String shortURL)
		throws NoSuchEntryException, SystemException {

		return shortLinkEntryPersistence.findBySURL_A(shortURL, false);
	}

	/**
	 * Factory method for a new ShortLinkEntry object.
	 *
	 * @return new object
	 */
	@Override
	public ShortLinkEntry shortLinkEntryFactory() {
		return super.createShortLinkEntry(0);
	}

	/**
	 * Method checks if the short link is already taken if it was modified. If
	 * the link is free it updates the entry. Also notifies the appropriate
	 * model listeners.
	 *
	 * @param shortLinkEntryId id of the entry to be updated
	 * @param originalURL original URL
	 * @param shortURL short URL
	 * @param active indication if this entry is active or not
	 * @return the link that was updated
	 * @throws SystemException if a system exception occurred
	 */
	public ShortLinkEntry updateShortLinkEntryWithCheck(
			long shortLinkEntryId, String originalURL,
			String shortURL, boolean active)
		throws ShortURLTakenException, SystemException {

		ShortLinkEntry originalLink = fetchShortLinkEntry(
			shortLinkEntryId);

		if (!originalLink.getShortURL().equals(shortURL)) {
			if (isShortURLNotUnique(shortURL)) {
				throw new ShortURLTakenException(shortURL);
			}
		}

		originalLink.setShortURL(shortURL);
		originalLink.setOriginalURL(originalURL);
		originalLink.setActive(active);
		originalLink.setModifiedDate(new Date());

		return super.updateShortLinkEntry(originalLink);
	}

	private ShortLinkEntry createNewShortLinkEntry(
			boolean autogenerated, String shortURL,String originalURL)
		throws SystemException {

		long linkId = counterLocalService.increment(
			ShortLinkEntry.class.getName());

		Date now = new Date();

		ShortLinkEntry result = shortLinkEntryPersistence.create(linkId);
		result.setCreateDate(now);
		result.setModifiedDate(now);
		result.setAutogenerated(autogenerated);
		result.setShortURL(shortURL);
		result.setOriginalURL(originalURL);
		result.setActive(true);
		return super.addShortLinkEntry(result);
	}

	private boolean isShortURLNotUnique(String shortURL)
		throws SystemException {

		return !shortLinkEntryPersistence.findBySURL(shortURL).isEmpty();
	}

	private static final String _DELETE_LINKS =
		ShortLinkEntryLocalServiceImpl.class.getName() +
			".deleteShortLinkEntries";

	private static Log _LOG = LogFactoryUtil.getLog(
		ShortLinkEntryLocalServiceImpl.class);

}