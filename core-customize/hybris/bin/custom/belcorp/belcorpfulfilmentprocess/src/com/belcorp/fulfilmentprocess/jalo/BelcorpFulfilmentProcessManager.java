/*
 * Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.belcorp.fulfilmentprocess.jalo;

import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.extension.ExtensionManager;
import com.belcorp.fulfilmentprocess.constants.BelcorpFulfilmentProcessConstants;

public class BelcorpFulfilmentProcessManager extends GeneratedBelcorpFulfilmentProcessManager
{
	public static final BelcorpFulfilmentProcessManager getInstance()
	{
		ExtensionManager em = JaloSession.getCurrentSession().getExtensionManager();
		return (BelcorpFulfilmentProcessManager) em.getExtension(BelcorpFulfilmentProcessConstants.EXTENSIONNAME);
	}
	
}
