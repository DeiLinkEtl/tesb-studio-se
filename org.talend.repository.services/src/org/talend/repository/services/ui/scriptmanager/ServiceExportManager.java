package org.talend.repository.services.ui.scriptmanager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.xml.namespace.QName;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.common.util.EMap;
import org.talend.commons.exception.SystemException;
import org.talend.core.model.properties.ProcessItem;
import org.talend.core.model.repository.IRepositoryViewObject;
import org.talend.designer.runprocess.IProcessor;
import org.talend.repository.model.IRepositoryNode.ENodeType;
import org.talend.repository.model.RepositoryNode;
import org.talend.repository.services.model.services.ServiceConnection;
import org.talend.repository.services.model.services.ServicePort;
import org.talend.repository.services.ui.ServiceMetadataDialog;
import org.talend.repository.services.utils.TemplateProcessor;
import org.talend.repository.services.utils.WSDLUtils;
import org.talend.repository.ui.wizards.exportjob.scriptsmanager.JobScriptsManager;
import org.talend.repository.ui.wizards.exportjob.scriptsmanager.esb.JobJavaScriptOSGIForESBManager;

public class ServiceExportManager extends JobJavaScriptOSGIForESBManager {

	private static final String TEMPLATE_SPRING_BEANS = "/resources/beans-template.xml"; //$NON-NLS-1$

	private static Logger logger = Logger.getLogger(ServiceExportManager.class);

	public ServiceExportManager() {
		super(null, null, null, IProcessor.NO_STATISTICS, IProcessor.NO_TRACES);
	}

	public void createSpringBeans(String outputFile, Map<ServicePort, Map<String, String>> ports,
			ServiceConnection serviceConnection, File wsdl, String studioServiceName)
			throws IOException, CoreException {

		//TODO: support multiport!!!
		Entry<ServicePort, Map<String, String>> studioPort = ports.entrySet().iterator().next();
		//TODO: do this in looooooooop!!!

		Definition def = WSDLUtils.getDefinition(wsdl.getAbsolutePath());
		String serviceName = null;
		String serviceNS = null;
		String endpointAddress = null;
		String endpointName = null;
		Map<QName, Service> services = def.getServices();
		ServicePort servicePort = studioPort.getKey();
		for (Entry<QName, Service> serviceEntry : services.entrySet()) { //TODO: support multi-services
			QName serviceQName = serviceEntry.getKey();
			Service service = serviceEntry.getValue();
			Collection<Port> servicePorts = service.getPorts().values(); //TODO: support multi-ports
			for (Port port : servicePorts) {
				if (servicePort.getName().equals(
						port.getBinding().getPortType().getQName().getLocalPart())) {
					serviceName = serviceQName.getLocalPart();
					serviceNS = serviceQName.getNamespaceURI();
					endpointName = port.getName();
					Collection<ExtensibilityElement> addrElems = findExtensibilityElement(
							port.getExtensibilityElements(), "address"); //$NON-NLS-1$
					for (ExtensibilityElement element : addrElems) {
						if (element != null && element instanceof SOAPAddress) {
							// http://jira.talendforge.org/browse/TESB-3638
							endpointAddress = ((SOAPAddress) element).getLocationURI();
							try {
								URL url = new URL(endpointAddress);
								endpointAddress = url.getPath();

								if (endpointAddress.equals("/services/")
										|| endpointAddress.equals("/services")) {
									// pass as is
								} else if (endpointAddress.startsWith("/services/")) {
									// remove forwarding "/services/" context as required by runtime
									endpointAddress = endpointAddress.substring(
											"/services/".length() - 1); // leave forwarding slash
								}
							} catch (MalformedURLException e) {
								logger.error("Endpoint URI invalid: " + e);
							}
						}
					}
				}
			}
		}


		Map<String, Object> endpointInfo = new HashMap<String, Object>();
		endpointInfo.put("namespace", serviceNS); //$NON-NLS-1$
		endpointInfo.put("service", serviceName); //$NON-NLS-1$
		endpointInfo.put("port", endpointName); //$NON-NLS-1$
		endpointInfo.put("address", endpointAddress); //$NON-NLS-1$
		endpointInfo.put("studioName", studioServiceName); //$NON-NLS-1$
		endpointInfo.put("wsdlLocation", wsdl.getName()); //$NON-NLS-1$

		Map<String, String> operation2job = new HashMap<String, String>();
		for (Map.Entry<ServicePort, Map<String, String>> port : ports.entrySet()) {
			//TODO: actual port work
			for (Map.Entry<String, String> operation : port.getValue().entrySet()) {
				operation2job.put(operation.getKey(), operation.getValue());
			}
		}
		endpointInfo.put("operation2job", operation2job); //$NON-NLS-1$


		EMap<String, String> additionalInfo = serviceConnection.getAdditionalInfo();
		endpointInfo.put("useSL",
				Boolean.valueOf(additionalInfo.get(ServiceMetadataDialog.USE_SL)));
		endpointInfo.put("useSAM",
				Boolean.valueOf(additionalInfo.get(ServiceMetadataDialog.USE_SAM)));
		endpointInfo.put("useSecurityToken",
				Boolean.valueOf(additionalInfo.get(ServiceMetadataDialog.SECURITY_BASIC)));
		endpointInfo.put("useSecuritySAML",
				Boolean.valueOf(additionalInfo.get(ServiceMetadataDialog.SECURITY_SAML)));

		Map<String, String> slCustomProperties = new HashMap<String, String>();
		if (Boolean.valueOf(additionalInfo.get(ServiceMetadataDialog.USE_SL))) {
			for (Map.Entry<String, String> prop : additionalInfo.entrySet()) {
				if (prop.getKey().startsWith(ServiceMetadataDialog.SL_CUSTOM_PROP_PREFIX)) {
					slCustomProperties.put(
							prop.getKey().substring(
									ServiceMetadataDialog.SL_CUSTOM_PROP_PREFIX.length()),
							prop.getValue());
				}
			}
		}
		endpointInfo.put("slCustomProps", slCustomProperties); //$NON-NLS-1$

		Map<String, Object> contextParams = new HashMap<String, Object>();
		contextParams.put("endpoint", endpointInfo);

		FileWriter fileWriter = null;
		try {
			fileWriter = new FileWriter(outputFile);
			TemplateProcessor.processTemplate(TEMPLATE_SPRING_BEANS, contextParams, fileWriter);
		} catch (SystemException e) {
			// something wrong with template processing
			logger.error(e.getLocalizedMessage(), e);
		} catch (IOException e) {
			// something wrong with output file
			logger.error(e.getLocalizedMessage(), e);
		} finally {
			if (null != fileWriter) {
				try {
					fileWriter.close();
				} catch (IOException e) {
					logger.warn(e.getLocalizedMessage(), e);
				}
			}
		}
	}

	public static Collection<ExtensibilityElement> findExtensibilityElement(
			List<ExtensibilityElement> extensibilityElements, String elementType) {
		List<ExtensibilityElement> elements = new ArrayList<ExtensibilityElement>();
		if (extensibilityElements != null) {
			for (ExtensibilityElement elment : extensibilityElements) {
				if (elment.getElementType().getLocalPart().equalsIgnoreCase(elementType)) {
					elements.add(elment);
				}
			}
		}
		return elements;
	}

	public Manifest getManifest(String artefactName, String serviceVersion) {
		Manifest manifest = new Manifest();
		Attributes a = manifest.getMainAttributes();
		a.put(Attributes.Name.MANIFEST_VERSION, "1.0"); //$NON-NLS-1$
		a.put(new Attributes.Name("Bundle-Name"), artefactName); //$NON-NLS-1$
		a.put(new Attributes.Name("Bundle-SymbolicName"), artefactName); //$NON-NLS-1$
		a.put(new Attributes.Name("Bundle-Version"), serviceVersion); //$NON-NLS-1$
		a.put(new Attributes.Name("Bundle-ManifestVersion"), "2"); //$NON-NLS-1$ //$NON-NLS-2$
		a.put(new Attributes.Name("Import-Package"), //$NON-NLS-1$
				"javax.xml.ws,org.talend.esb.job.controller" //$NON-NLS-1$
				+ ",org.osgi.service.cm;version=\"[1.3,2)\"" //$NON-NLS-1$
				+ ",org.apache.ws.security.validate" //$NON-NLS-1$
			);
		a.put(new Attributes.Name("Require-Bundle"), //$NON-NLS-1$
				"org.apache.cxf.bundle" //$NON-NLS-1$
				+ ",org.springframework.beans" //$NON-NLS-1$
				+ ",org.springframework.context" //$NON-NLS-1$
				+ ",org.springframework.osgi.core" //$NON-NLS-1$
				+ ",locator" //$NON-NLS-1$
				+ ",sam-agent" //$NON-NLS-1$
				+ ",sam-common" //$NON-NLS-1$
			);
		return manifest;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.talend.repository.ui.wizards.exportjob.scriptsmanager.esb.
	 * JobJavaScriptOSGIForESBManager#getOutputSuffix()
	 */
	@Override
	public String getOutputSuffix() {
		return "/";
	}

	public static Map<ExportChoice, Object> getDefaultExportChoiceMap() {
		Map<ExportChoice, Object> exportChoiceMap =
				new EnumMap<ExportChoice, Object>(ExportChoice.class);
		exportChoiceMap.put(ExportChoice.needLauncher, true);
		exportChoiceMap.put(ExportChoice.needSystemRoutine, true);
		exportChoiceMap.put(ExportChoice.needUserRoutine, true);
		exportChoiceMap.put(ExportChoice.needTalendLibraries, true);
		exportChoiceMap.put(ExportChoice.needJobItem, true);
		exportChoiceMap.put(ExportChoice.needJobScript, true);
		exportChoiceMap.put(ExportChoice.needContext, true);
		exportChoiceMap.put(ExportChoice.needSourceCode, true);
		exportChoiceMap.put(ExportChoice.applyToChildren, false);
		exportChoiceMap.put(ExportChoice.doNotCompileCode, false);
		return exportChoiceMap;
	}

	public JobScriptsManager getJobManager(RepositoryNode node, String groupId,
			String serviceVersion) {
		JobJavaScriptOSGIForESBManager manager = new JobJavaScriptOSGIForESBManager(
				getDefaultExportChoiceMap(), "Default", serviceVersion,
				statisticPort, tracePort);
		String artefactName = getNodeLabel(node);
		File path = getFilePath(groupId, artefactName, serviceVersion);
		File file = new File(path, artefactName + "-" + serviceVersion
				+ manager.getOutputSuffix());
		manager.setDestinationPath(file.getAbsolutePath());
		return manager;
	}

	public File getFilePath(String groupId, String artefactName,
			String serviceVersion) {
		File folder = new File(getDestinationPath(), "repository");
		folder.mkdirs();
		String path = groupId.replace('.', File.separatorChar);
		File group = new File(folder, path);
		File artefact = new File(group, artefactName);
		File version = new File(artefact, serviceVersion);
		version.mkdirs();
		return version;
	}

	public String getNodeLabel(RepositoryNode node) {
		if (node.getType() == ENodeType.REPOSITORY_ELEMENT) {
			IRepositoryViewObject repositoryObject = node.getObject();
			if (repositoryObject.getProperty().getItem() instanceof ProcessItem) {
				ProcessItem processItem = (ProcessItem) repositoryObject
						.getProperty().getItem();
				return processItem.getProperty().getLabel();
			}
		}
		return "Job";
	}

}
