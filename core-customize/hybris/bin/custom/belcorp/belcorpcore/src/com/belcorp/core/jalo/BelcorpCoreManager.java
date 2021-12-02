/*
 * Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.belcorp.core.jalo;

import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.extension.ExtensionManager;
import com.belcorp.core.constants.BelcorpCoreConstants;
import com.belcorp.core.setup.CoreSystemSetup;


/**
 * Do not use, please use {@link CoreSystemSetup} instead.
 * 
 */
public class BelcorpCoreManager extends GeneratedBelcorpCoreManager
{
	public static final BelcorpCoreManager getInstance()
	{
		final ExtensionManager em = JaloSession.getCurrentSession().getExtensionManager();
		return (BelcorpCoreManager) em.getExtension(BelcorpCoreConstants.EXTENSIONNAME);
	}
}
