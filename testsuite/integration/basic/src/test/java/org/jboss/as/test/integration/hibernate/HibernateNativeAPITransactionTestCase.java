package org.jboss.as.test.integration.hibernate;

import static org.junit.Assert.assertTrue;

import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test operations including rollback using Hibernate transaction and Sessionfactory inititated from hibernate.cfg.xml and
 * properties added to Hibernate Configuration in AS7 container without any JPA assistance
 * 
 * @author Madhumita Sadhukhan
 */
@RunWith(Arquillian.class)
public class HibernateNativeAPITransactionTestCase {

    private static final String ARCHIVE_NAME = "hibernate4native_transactiontest";

    public static final String hibernate_cfg = "<?xml version='1.0' encoding='utf-8'?>"
            + "<!DOCTYPE hibernate-configuration PUBLIC " + "\"//Hibernate/Hibernate Configuration DTD 3.0//EN\" "
            + "\"http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd\">"
            + "<hibernate-configuration><session-factory>" + "<property name=\"show_sql\">true</property>"
            + "<property name=\"current_session_context_class\">thread</property>"
            + "<mapping resource=\"testmapping.hbm.xml\"/>" + "</session-factory></hibernate-configuration>";

    public static final String testmapping = "<?xml version=\"1.0\"?>" + "<!DOCTYPE hibernate-mapping PUBLIC "
            + "\"-//Hibernate/Hibernate Mapping DTD 3.0//EN\" " + "\"http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd\">"
            + "<hibernate-mapping package=\"org.jboss.as.test.integration.nonjpa.hibernate\">"
            + "<class name=\"org.jboss.as.test.integration.hibernate.Student\" table=\"STUDENT\">"
            + "<id name=\"studentId\" column=\"student_id\">" + "<generator class=\"native\"/>" + "</id>"
            + "<property name=\"firstName\" column=\"first_name\"/>" + "<property name=\"lastName\" column=\"last_name\"/>"
            + "<property name=\"address\"/>"
            // + "<set name=\"courses\" table=\"student_courses\">"
            // + "<key column=\"student_id\"/>"
            // + "<many-to-many column=\"course_id\" class=\"org.jboss.as.test.integration.nonjpa.hibernate.Course\"/>"
            // + "</set>" +
            + "</class></hibernate-mapping>";

    @ArquillianResource
    private static InitialContext iniCtx;

    @BeforeClass
    public static void beforeClass() throws NamingException {
        iniCtx = new InitialContext();
    }

    @Deployment
    public static Archive<?> deploy() throws Exception {

        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, ARCHIVE_NAME + ".ear");
        // add required jars as manifest dependencies
        ear.addAsManifestResource(
                new StringAsset(
                        "Dependencies: javax.xml.bind.api export,org.dom4j export,org.javassist export,org.antlr export,org.apache.commons.collections export,org.jboss.jandex export,org.hibernate.envers export,org.hibernate\n"),
                "MANIFEST.MF");

        JavaArchive lib = ShrinkWrap.create(JavaArchive.class, "beans.jar");
        lib.addClasses(SFSBHibernateTransaction.class);
        ear.addAsModule(lib);

        lib = ShrinkWrap.create(JavaArchive.class, "entities.jar");
        lib.addClasses(Student.class);
        lib.addAsResource(new StringAsset(testmapping), "testmapping.hbm.xml");
        lib.addAsResource(new StringAsset(hibernate_cfg), "hibernate.cfg.xml");
        ear.addAsLibraries(lib);

        final WebArchive main = ShrinkWrap.create(WebArchive.class, "main.war");
        main.addClasses(HibernateNativeAPITransactionTestCase.class);
        ear.addAsModule(main);

        // add application dependency on H2 JDBC driver, so that the Hibernate classloader (same as app classloader)
        // will see the H2 JDBC driver.
        // equivalent hack for use of shared Hiberante module, would be to add the H2 dependency directly to the
        // shared Hibernate module.
        // also add dependency on org.slf4j
        ear.addAsManifestResource(new StringAsset("<jboss-deployment-structure>" + " <deployment>" + " <dependencies>"
                + " <module name=\"com.h2database.h2\" />" + " <module name=\"org.slf4j\"/>" + " </dependencies>"
                + " </deployment>" + "</jboss-deployment-structure>"), "jboss-deployment-structure.xml");

        return ear;
    }

    protected static <T> T lookup(String beanName, Class<T> interfaceType) throws NamingException {
        try {
            return interfaceType.cast(iniCtx.lookup("java:global/" + ARCHIVE_NAME + "/" + "beans/" + beanName + "!"
                    + interfaceType.getName()));
        } catch (NamingException e) {
            dumpJndi("");
            throw e;
        }
    }

    // TODO: move this logic to a common base class (might be helpful for writing new tests)
    private static void dumpJndi(String s) {
        try {
            dumpTreeEntry(iniCtx.list(s), s);
        } catch (NamingException ignore) {
        }
    }

    private static void dumpTreeEntry(NamingEnumeration<NameClassPair> list, String s) throws NamingException {
        System.out.println("\ndump " + s);
        while (list.hasMore()) {
            NameClassPair ncp = list.next();
            System.out.println(ncp.toString());
            if (s.length() == 0) {
                dumpJndi(ncp.getName());
            } else {
                dumpJndi(s + "/" + ncp.getName());
            }
        }
    }

    @Test
    public void testSimpleOperation() throws Exception {
        SFSBHibernateTransaction sfsb = lookup("SFSBHibernateTransaction", SFSBHibernateTransaction.class);
        // setup Configuration and SessionFactory
        sfsb.setupConfig();
        Student s1 = sfsb.createStudent("MADHUMITA", "SADHUKHAN", "99 Purkynova REDHAT BRNO CZ", 1);
        Student s2 = sfsb.createStudent("REDHAT", "LINUX", "Worldwide", 3);
        assertTrue("address read from hibernate session associated with hibernate transaction is 99 Purkynova REDHAT BRNO CZ",
                "99 Purkynova REDHAT BRNO CZ".equals(s1.getAddress()));
        // update Student
        Student s3 = sfsb.updateStudent("REDHAT RALEIGH, NORTH CAROLINA", 1);
        Student st = sfsb.getStudentNoTx(s1.getStudentId());
        assertTrue(
                "address read from hibernate session associated with hibernate transaction is REDHAT RALEIGH, NORTH CAROLINA",
                "REDHAT RALEIGH, NORTH CAROLINA".equals(st.getAddress()));
    }

    // tests rollback
    @Test
    public void testRollBackOperation() throws Exception {
        SFSBHibernateTransaction sfsb = lookup("SFSBHibernateTransaction", SFSBHibernateTransaction.class);
        // setup Configuration and SessionFactory
        sfsb.setupConfig();
        Student s2 = sfsb.createStudent("REDHAT", "LINUX", "Worldwide", 3);
        // force creation of student with same Id to ensure RollBack
        Student s3 = sfsb.createStudent("Hibernate", "ORM", "JavaWorld", s2.getStudentId());
        Student st = sfsb.getStudentNoTx(s2.getStudentId());
        assertTrue("name read from hibernate session associated with hibernate transaction after rollback is REDHAT",
                "REDHAT".equals(st.getFirstName()));
    }

}
