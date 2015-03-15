/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.as.test.integration.ws.wsse.signencrypt;

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.cxf.ws.security.SecurityConstants;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.ws.wsse.EJBEncryptServiceImpl;
import org.jboss.as.test.integration.ws.wsse.KeystorePasswordCallback;
import org.jboss.as.test.integration.ws.wsse.ServiceIface;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test WS sign + encrypt capability for multiple clients (alice and john)
 *
 * Certificates can ge generated using keytool -genkey -keyalg RSA -storetype JKS
 * Public key can be extracted using keytool -export
 * Public key can be imported using keytool -import
 * Keystore can be listed using keytool -list -v
 *
 * @author Rostislav Svoboda
 */
@RunWith(Arquillian.class)
@RunAsClient
public class EJBSignEncryptMultipleClientsTestCase {

    private static Logger log = Logger.getLogger(EJBSignEncryptMultipleClientsTestCase.class.getName());
    @ArquillianResource
    URL baseUrl;

    @Deployment
    public static Archive<?> deployment() {

        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "jaxws-wsse-sign-encrypt.jar").
                addAsManifestResource(new StringAsset("Dependencies: org.apache.ws.security\n"), "MANIFEST.MF").
                addClasses(ServiceIface.class, EJBEncryptServiceImpl.class, KeystorePasswordCallback.class).
                addAsResource(ServiceIface.class.getPackage(), "bob.jks", "bob.jks").
                addAsResource(ServiceIface.class.getPackage(), "bob.properties", "bob.properties").
                addAsManifestResource(ServiceIface.class.getPackage(), "wsdl/SecurityService-ejb-sign-encrypt.wsdl", "wsdl/SecurityService.wsdl").
                addAsManifestResource(ServiceIface.class.getPackage(), "wsdl/SecurityService_schema1.xsd", "wsdl/SecurityService_schema1.xsd").
                addAsManifestResource(EJBSignEncryptMultipleClientsTestCase.class.getPackage(), "multiple-clients-jaxws-endpoint-config.xml", "jaxws-endpoint-config.xml");

        log.info(jar.toString(true));

        return jar;
    }

    @Test
    public void encryptedAndSignedRequestFromAlice() throws Exception {
        QName serviceName = new QName("http://www.jboss.org/jbossws/ws-extensions/wssecuritypolicy", "EJBEncryptSecurityService");
        URL wsdlURL = new URL(baseUrl, "/jaxws-wsse-sign-encrypt/EJBEncryptSecurityService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        ServiceIface proxy = (ServiceIface) service.getPort(ServiceIface.class);
        setupWsse(proxy, "alice");

        Assert.assertEquals("Secure Hello World!", proxy.sayHello());
    }

    @Ignore("WFLY-3872")
    @Test
    public void encryptedAndSignedRequestFromJohn() throws Exception {
        QName serviceName = new QName("http://www.jboss.org/jbossws/ws-extensions/wssecuritypolicy", "EJBEncryptSecurityService");
        URL wsdlURL = new URL(baseUrl, "/jaxws-wsse-sign-encrypt/EJBEncryptSecurityService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        ServiceIface proxy = (ServiceIface) service.getPort(ServiceIface.class);
        setupWsse(proxy, "john");

        Assert.assertEquals("Secure Hello World!", proxy.sayHello());
    }

    /*
     * Max's public key is not trusted in Bob's keystore
     * Max's keystore contain's Bob's public key as trusted.
     */
    @Test
    public void encryptedAndSignedRequestFromUntrustedMax() throws Exception {
        QName serviceName = new QName("http://www.jboss.org/jbossws/ws-extensions/wssecuritypolicy", "EJBEncryptSecurityService");
        URL wsdlURL = new URL(baseUrl, "/jaxws-wsse-sign-encrypt/EJBEncryptSecurityService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        ServiceIface proxy = (ServiceIface) service.getPort(ServiceIface.class);
        setupWsse(proxy, "max");

        try {
            proxy.sayHello();
            Assert.fail("Max shouldn't invoke this service");
        } catch (SOAPFaultException ex) {
            // expected failure because max isn't trusted
        }
    }

    private void setupWsse(ServiceIface proxy, String clientId) throws MalformedURLException {
        ((BindingProvider) proxy).getRequestContext().put(SecurityConstants.CALLBACK_HANDLER, new KeystorePasswordCallback());
        ((BindingProvider) proxy).getRequestContext().put(SecurityConstants.SIGNATURE_PROPERTIES, "org/jboss/as/test/integration/ws/wsse/" + clientId + ".properties");
        ((BindingProvider) proxy).getRequestContext().put(SecurityConstants.ENCRYPT_PROPERTIES, "org/jboss/as/test/integration/ws/wsse/" + clientId + ".properties");
        ((BindingProvider) proxy).getRequestContext().put(SecurityConstants.SIGNATURE_USERNAME, clientId);
        ((BindingProvider) proxy).getRequestContext().put(SecurityConstants.ENCRYPT_USERNAME, "bob");
    }
}