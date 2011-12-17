// ============================================================================
//
// Copyright (C) 2006-2010 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.camel.designer.util;

import org.talend.core.model.repository.ERepositoryObjectType;

/**
 * DOC guanglong.du class global comment. Detailled comment
 */
public class CamelRepositoryNodeType {

    public static final String ROUTES = "ROUTES";

    public static final String BEANS = "BEANS";

    public static ERepositoryObjectType repositoryRoutesType = (ERepositoryObjectType) ERepositoryObjectType.valueOf(
            ERepositoryObjectType.class, ROUTES);

    public static ERepositoryObjectType repositoryBeansType = (ERepositoryObjectType) ERepositoryObjectType.valueOf(
            ERepositoryObjectType.class, BEANS);
}
