/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.provisioning.impl.dummy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.identityconnectors.framework.common.objects.OperationalAttributes.ENABLE_DATE_NAME;
import static org.testng.AssertJUnit.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.evolveum.icf.dummy.resource.*;
import com.evolveum.midpoint.provisioning.api.LiveSyncEvent;
import com.evolveum.midpoint.provisioning.api.LiveSyncEventHandler;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.provisioning.api.ProvisioningOperationOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.test.DummyTestResource;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;

/**
 * Tests the behavior of provisioning module under erroneous conditions
 * (including invalid API calls).
 *
 * @author Radovan Semancik
 */
@ContextConfiguration(locations = "classpath:ctx-provisioning-test-main.xml")
@DirtiesContext
public class TestDummyNegative extends AbstractDummyTest {

    private static final File ACCOUNT_ELAINE_RESOURCE_NOT_FOUND_FILE =
            new File(TEST_DIR, "account-elaine-resource-not-found.xml");

    private static final String ATTR_NUMBER = "number";

    private static final DummyTestResource RESOURCE_DUMMY_BROKEN_ACCOUNTS = new DummyTestResource(
            TEST_DIR, "resource-dummy-broken-accounts.xml", "202db5cf-f3c2-437c-9354-64054343d37d", "broken-accounts",
            TestDummyNegative::addFragileAttributes
    );

    private static final DummyTestResource RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID = new DummyTestResource(
            TEST_DIR, "resource-dummy-broken-accounts-external-uid.xml", "6139ea00-fc2a-4a68-b830-3e4012c766ee", "broken-accounts-external-uid",
            TestDummyNegative::addFragileAttributes
    );

    private static void addFragileAttributes(DummyResourceContoller controller) throws ConnectException, FileNotFoundException, SchemaViolationException, ConflictException, InterruptedException {
        // This gives us a potential to induce exceptions during ConnId->object conversion.
        controller.addAttrDef(controller.getDummyResource().getAccountObjectClass(),
                ENABLE_DATE_NAME, Long.class, false, false);

        // This is a secondary identifier which gives us a potential to induce exceptions during repo shadow manipulation.
        controller.addAttrDef(controller.getDummyResource().getAccountObjectClass(),
                ATTR_NUMBER, Integer.class, false, false);
    }

    private static final String GOOD_ACCOUNT = "good";
    private static final String INCONVERTIBLE_ACCOUNT = "inconvertible";
    private static final String UNSTORABLE_ACCOUNT = "unstorable";
    private static final String TOTALLY_UNSTORABLE_ACCOUNT = "totally-unstorable" + StringUtils.repeat("-123456789", 30); // too large to be stored in DB

    private static final String EXTERNAL_UID_PREFIX = "uid:";
    private static final String GOOD_ACCOUNT_UID = EXTERNAL_UID_PREFIX + GOOD_ACCOUNT;
    private static final String INCONVERTIBLE_ACCOUNT_UID = EXTERNAL_UID_PREFIX + INCONVERTIBLE_ACCOUNT;
    private static final String UNSTORABLE_ACCOUNT_UID = EXTERNAL_UID_PREFIX + UNSTORABLE_ACCOUNT;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);

        initDummyResource(RESOURCE_DUMMY_BROKEN_ACCOUNTS, initResult);
        testResourceAssertSuccess(RESOURCE_DUMMY_BROKEN_ACCOUNTS, initTask);

        initDummyResource(RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID, initResult);
        testResourceAssertSuccess(RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID, initTask);
    }

    //region Tests for broken schema (in various ways)
    @Test
    public void test110GetResourceBrokenSchemaNetwork() throws Exception {
        testGetResourceBrokenSchema(BreakMode.NETWORK);
    }

    @Test
    public void test111GetResourceBrokenSchemaGeneric() throws Exception {
        testGetResourceBrokenSchema(BreakMode.GENERIC);
    }

    @Test
    public void test112GetResourceBrokenSchemaIo() throws Exception {
        testGetResourceBrokenSchema(BreakMode.IO);
    }

    @Test
    public void test113GetResourceBrokenSchemaRuntime() throws Exception {
        testGetResourceBrokenSchema(BreakMode.RUNTIME);
    }

    private void testGetResourceBrokenSchema(BreakMode breakMode) throws Exception {
        given();
        OperationResult result = createOperationResult();

        // precondition
        PrismObject<ResourceType> repoResource = repositoryService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, result);
        display("Repo resource (before)", repoResource);
        PrismContainer<Containerable> schema = repoResource.findContainer(ResourceType.F_SCHEMA);
        assertTrue("Schema found in resource before the test (precondition)", schema == null || schema.isEmpty());

        dummyResource.setSchemaBreakMode(breakMode);
        try {

            when();
            PrismObject<ResourceType> resource = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, null, result);

            then();
            display("Resource with broken schema", resource);
            OperationResultType fetchResult = resource.asObjectable().getFetchResult();

            result.computeStatus();
            display("getObject result", result);
            assertEquals("Unexpected result of getObject operation", OperationResultStatus.PARTIAL_ERROR, result.getStatus());

            assertNotNull("No fetch result", fetchResult);
            display("fetchResult", fetchResult);
            assertEquals("Unexpected result of fetchResult", OperationResultStatusType.PARTIAL_ERROR, fetchResult.getStatus());

        } finally {
            dummyResource.setSchemaBreakMode(BreakMode.NONE);
        }
    }

    /**
     * Finally, no errors! Here we simply get a resource with no obstacles.
     * This also prepares the stage for further tests.
     */
    @Test
    public void test190GetResource() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        dummyResource.setSchemaBreakMode(BreakMode.NONE);
        syncServiceMock.reset();

        when();
        PrismObject<ResourceType> resource = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, task, result);

        then();
        assertSuccess(result);

        display("Resource after", resource);
        IntegrationTestTools.displayXml("Resource after (XML)", resource);
        assertHasSchema(resource, "dummy");
    }
    //endregion

    //region Tests for adding/removing/getting/searching for broken accounts (in various ways)
    @Test
    public void test200AddAccountNullAttributes() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ShadowType accountType = parseObjectType(ACCOUNT_WILL_FILE, ShadowType.class);
        PrismObject<ShadowType> account = accountType.asPrismObject();
        account.checkConsistence();
        account.removeContainer(ShadowType.F_ATTRIBUTES);
        display("Adding shadow", account);

        try {
            when();
            provisioningService.addObject(account, null, null, task, result);

            assertNotReached();
        } catch (SchemaException e) {
            displayExpectedException(e);
        }

        then();
        syncServiceMock.assertSingleNotifyFailureOnly();
    }

    @Test
    public void test201AddAccountEmptyAttributes() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = getTestOperationResult();
        syncServiceMock.reset();

        ShadowType accountType = parseObjectType(ACCOUNT_WILL_FILE, ShadowType.class);
        PrismObject<ShadowType> account = accountType.asPrismObject();
        account.checkConsistence();

        account.findContainer(ShadowType.F_ATTRIBUTES).getValue().clear();

        display("Adding shadow", account);

        try {
            when();
            provisioningService.addObject(account, null, null, task, result);

            AssertJUnit.fail("The addObject operation was successful. But expecting an exception.");
        } catch (SchemaException e) {
            displayExpectedException(e);
        }

        then();
        syncServiceMock.assertSingleNotifyFailureOnly();
    }

    @Test
    public void test210AddAccountNoObjectClass() throws Exception {
        given();
        Task task =getTestTask();
        OperationResult result = getTestOperationResult();
        syncServiceMock.reset();

        ShadowType accountType = parseObjectType(ACCOUNT_WILL_FILE, ShadowType.class);
        PrismObject<ShadowType> account = accountType.asPrismObject();
        account.checkConsistence();

        // IMPORTANT: deliberately violating the schema
        accountType.setObjectClass(null);
        accountType.setKind(null);

        display("Adding shadow", account);

        try {
            when();
            provisioningService.addObject(account, null, null, task, result);

            AssertJUnit.fail("The addObject operation was successful. But expecting an exception.");
        } catch (SchemaException e) {
            displayExpectedException(e);
        }

        then();
        syncServiceMock.assertSingleNotifyFailureOnly();
    }

    /**
     * Adding an account without resourceRef.
     */
    @Test
    public void test220AddAccountNoResourceRef() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ShadowType accountType = parseObjectType(ACCOUNT_WILL_FILE, ShadowType.class);
        PrismObject<ShadowType> account = accountType.asPrismObject();
        account.checkConsistence();

        accountType.setResourceRef(null);

        display("Adding shadow", account);

        try {
            when();
            provisioningService.addObject(account, null, null, task, result);

            AssertJUnit.fail("The addObject operation was successful. But expecting an exception.");
        } catch (SchemaException e) {
            displayExpectedException(e);
        }

        //FIXME: not sure, if this check is needed..if the resource is not specified, provisioning probably will be not called.
//        syncServiceMock.assertNotifyFailureOnly();
    }

    /**
     * Deleting an account with resourceRef pointing to non-existent resource.
     */
    @Test
    public void test221DeleteAccountResourceNotFound() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ShadowType accountType = parseObjectType(ACCOUNT_ELAINE_RESOURCE_NOT_FOUND_FILE);
        PrismObject<ShadowType> account = accountType.asPrismObject();
        account.checkConsistence();

        display("Adding shadow", account);

        try {
            when();
            String oid = repositoryService.addObject(account, null, result);
            ProvisioningOperationOptions options = ProvisioningOperationOptions.createForce(true);
            provisioningService.deleteObject(ShadowType.class, oid, options, null, task, result);
        } catch (SchemaException e) {
            displayExpectedException(e);
        }

        //FIXME: is this really notify failure? the resource does not exist but shadow is deleted. maybe other case of notify?
//        syncServiceMock.assertNotifyFailureOnly();
    }

    /**
     * Try to get an account when a shadow has been deleted (but the account exists).
     * Proper ObjectNotFoundException is expected, compensation should not run.
     */
    @Test
    public void test230GetAccountDeletedShadow() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        PrismObject<ShadowType> account = PrismTestUtil.parseObject(ACCOUNT_MORGAN_FILE);
        String shadowOid = provisioningService.addObject(account, null, null, task, result);

        repositoryService.deleteObject(ShadowType.class, shadowOid, result);

        syncServiceMock.reset();

        try {
            when();
            provisioningService.getObject(ShadowType.class, shadowOid, null, task, result);

            assertNotReached();
        } catch (ObjectNotFoundException e) {
            displayExpectedException(e);
        }

        then();
        assertFailure(result);

        syncServiceMock.assertNoNotifyChange();
    }

    /**
     * Checks the behaviour when getting broken accounts, i.e. accounts that cannot be retrieved
     * because of e.g.
     *
     * - inability to convert from ConnId to resource object (`ShadowType`)
     * - inability to create or update midPoint shadow
     *
     * The current behavior is that `getObject` throws an exception in these cases.
     * This may or may not be ideal. We can consider changing that (to signalling via `fetchResult`)
     * later. But that would mean adapting the clients so that they would check the `fetchResult`
     * and use the resulting shadow only if it's OK.
     *
     * Because the `getObject` operation requires a shadow to exists, we have to create these objects
     * in a good shape, retrieve them, and break them afterwards.
     */
    @Test
    public void test240GetBrokenAccounts() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        DummyResourceContoller controller = RESOURCE_DUMMY_BROKEN_ACCOUNTS.controller;
        DummyResource resource = controller.getDummyResource();

        // create good accounts
        createAccount(GOOD_ACCOUNT, 1, null);
        createAccount(INCONVERTIBLE_ACCOUNT, 2, null);
        createAccount(UNSTORABLE_ACCOUNT, 3, null);

        // here we create the shadows
        SearchResultList<PrismObject<ShadowType>> accounts =
                provisioningService.searchObjects(ShadowType.class, getAllAccountsQuery(RESOURCE_DUMMY_BROKEN_ACCOUNTS),
                        null, task, result);
        String goodOid = selectAccountByName(accounts, GOOD_ACCOUNT).getOid();
        String inconvertibleOid = selectAccountByName(accounts, INCONVERTIBLE_ACCOUNT).getOid();
        String unstorableOid = selectAccountByName(accounts, UNSTORABLE_ACCOUNT).getOid();

        // break the accounts
        resource.getAccountByUsername(INCONVERTIBLE_ACCOUNT).replaceAttributeValue(ENABLE_DATE_NAME, "WRONG");
        resource.getAccountByUsername(UNSTORABLE_ACCOUNT).replaceAttributeValue(ATTR_NUMBER, "WRONG");

        when(GOOD_ACCOUNT);
        PrismObject<ShadowType> goodReloaded = provisioningService.getObject(ShadowType.class, goodOid, null, task, result);

        then(GOOD_ACCOUNT);
        assertShadow(goodReloaded, GOOD_ACCOUNT)
                .assertSuccessOrNoFetchResult();

        when(INCONVERTIBLE_ACCOUNT);
        try {
            provisioningService.getObject(ShadowType.class, inconvertibleOid, null, task, result);
            assertNotReached();
        } catch (SchemaException e) {
            then(INCONVERTIBLE_ACCOUNT);
            displayExpectedException(e);

            // Note: this is the current implementation. We might change it to return something,
            // and fill-in fetchResult appropriately.
        }

        when(UNSTORABLE_ACCOUNT);
        try {
            provisioningService.getObject(ShadowType.class, unstorableOid, null, task, result);
            assertNotReached();
        } catch (Exception e) {
            then(UNSTORABLE_ACCOUNT);
            displayExpectedException(e);

            // Note: this is the current implementation. We might change it to return something,
            // and fill-in fetchResult appropriately.
        }
    }

    @Test
    public void test250SearchForBrokenAccounts() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        cleanupAccounts(RESOURCE_DUMMY_BROKEN_ACCOUNTS, result);

        createAccount(GOOD_ACCOUNT, 1, null);
        createAccount(INCONVERTIBLE_ACCOUNT, 2, "WRONG");
        createAccount(UNSTORABLE_ACCOUNT, "WRONG", null);
        createAccount(TOTALLY_UNSTORABLE_ACCOUNT, 4, null);

        when();

        List<PrismObject<ShadowType>> objects = new ArrayList<>();

        ResultHandler<ShadowType> handler = (object, parentResult) -> {
            objects.add(object);
            return true;
        };
        Collection<SelectorOptions<GetOperationOptions>> options = createNoExceptionOptions();
        provisioningService.searchObjectsIterative(ShadowType.class, getAllAccountsQuery(RESOURCE_DUMMY_BROKEN_ACCOUNTS),
                options, handler, task, result);

        then();
        display("objects", objects);
        assertThat(objects.size()).as("objects found").isEqualTo(4);

        assertSelectedAccountByName(objects, GOOD_ACCOUNT)
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(GOOD_ACCOUNT)
                .attributes()
                    .assertSize(3)
                    .end()
                .assertSuccessOrNoFetchResult();

        PrismObject<ShadowType> goodAfter = findShadowByPrismName(GOOD_ACCOUNT, RESOURCE_DUMMY_BROKEN_ACCOUNTS.object, result);
        assertShadow(goodAfter, GOOD_ACCOUNT)
                .display()
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(GOOD_ACCOUNT)
                .attributes()
                    .assertSize(3)
                    .end();

        assertSelectedAccountByName(objects, INCONVERTIBLE_ACCOUNT)
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(INCONVERTIBLE_ACCOUNT)
                .attributes()
                    .assertSize(2) // uid=inconvertible + number=2
                    .end()
                .assertFetchResult(OperationResultStatusType.FATAL_ERROR, "Couldn't convert resource object", INCONVERTIBLE_ACCOUNT);
                // (maybe it's not necessary to provide account attributes in the message - reconsider)

        PrismObject<ShadowType> inconvertibleAfter = findShadowByPrismName(INCONVERTIBLE_ACCOUNT,
                RESOURCE_DUMMY_BROKEN_ACCOUNTS.object, result);
        assertShadow(inconvertibleAfter, INCONVERTIBLE_ACCOUNT)
                .display()
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(INCONVERTIBLE_ACCOUNT)
                .attributes()
                    .assertSize(2)
                    .end();

        assertSelectedAccountByName(objects, UNSTORABLE_ACCOUNT)
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(UNSTORABLE_ACCOUNT)
                .attributes()
                    .assertSize(1) // uid=unstorable [name was probably removed when we attempted to save the object?]
                    .end()
                .assertFetchResult(OperationResultStatusType.FATAL_ERROR, "Exception when translating", "WRONG");
                // (maybe it's not necessary to provide the unconvertible value in the message - reconsider)

        PrismObject<ShadowType> unstorableAfter = findShadowByPrismName(UNSTORABLE_ACCOUNT,
                RESOURCE_DUMMY_BROKEN_ACCOUNTS.object, result);
        assertShadow(unstorableAfter, UNSTORABLE_ACCOUNT)
                .display()
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(UNSTORABLE_ACCOUNT)
                .attributes()
                    .assertSize(1)
                    .end();

        assertSelectedAccountByName(objects, TOTALLY_UNSTORABLE_ACCOUNT)
                .assertNoOid()
                // Primary identifier value is not here, because it is set as part of object shadowization (which failed)
                .attributes()
                    .assertSize(3) // number, name, uid
                    .end()
                .assertFetchResult(OperationResultStatusType.FATAL_ERROR, "could not execute batch");
    }

    @NotNull
    private Collection<SelectorOptions<GetOperationOptions>> createNoExceptionOptions() {
        return schemaService.getOperationOptionsBuilder()
                .errorReportingMethod(FetchErrorReportingMethodType.FETCH_RESULT)
                .build();
    }

    @Test
    public void test260SearchForBrokenAccountsExternalUid() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        cleanupAccounts(RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID, result);

        createAccountExternalUid(GOOD_ACCOUNT, 1, null);
        createAccountExternalUid(INCONVERTIBLE_ACCOUNT, 2, "WRONG");
        createAccountExternalUid(UNSTORABLE_ACCOUNT, "WRONG", null);
        createAccountExternalUid(TOTALLY_UNSTORABLE_ACCOUNT, 4, null);

        when();

        List<PrismObject<ShadowType>> objects = new ArrayList<>();

        ResultHandler<ShadowType> handler = (object, parentResult) -> {
            objects.add(object);
            return true;
        };
        Collection<SelectorOptions<GetOperationOptions>> options = createNoExceptionOptions();
        provisioningService.searchObjectsIterative(ShadowType.class, getAllAccountsQuery(RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID),
                options, handler, task, result);

        then();
        display("objects", objects);
        assertThat(objects.size()).as("objects found").isEqualTo(4);

        assertSelectedAccountByName(objects, GOOD_ACCOUNT)
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(GOOD_ACCOUNT_UID)
                .assertName(GOOD_ACCOUNT)
                .attributes()
                    .assertSize(3)
                    .end()
                .assertSuccessOrNoFetchResult();

        PrismObject<ShadowType> goodAfter = findShadowByPrismName(GOOD_ACCOUNT, RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID.object, result);
        assertShadow(goodAfter, GOOD_ACCOUNT)
                .display()
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(GOOD_ACCOUNT_UID)
                .attributes()
                    .assertSize(3)
                    .end();

        assertSelectedAccountByName(objects, INCONVERTIBLE_ACCOUNT_UID)
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(INCONVERTIBLE_ACCOUNT_UID)
                .assertName(INCONVERTIBLE_ACCOUNT_UID)
                .attributes()
                    .assertSize(2) // uid=uid:inconvertible + number=2
                    .end()
                .assertFetchResult(OperationResultStatusType.FATAL_ERROR, "Couldn't convert resource object", INCONVERTIBLE_ACCOUNT);
                // (maybe it's not necessary to provide account attributes in the message - reconsider)

        // name is now derived from UID
        PrismObject<ShadowType> inconvertibleAfter = findShadowByPrismName(INCONVERTIBLE_ACCOUNT_UID,
                RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID.object, result);
        assertShadow(inconvertibleAfter, INCONVERTIBLE_ACCOUNT)
                .display()
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(INCONVERTIBLE_ACCOUNT_UID)
                .attributes()
                    .assertSize(2)
                    .end();

        assertSelectedAccountByName(objects, UNSTORABLE_ACCOUNT_UID)
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(UNSTORABLE_ACCOUNT_UID)
                .assertName(UNSTORABLE_ACCOUNT_UID)
                .attributes()
                    .assertSize(1) // uid=unstorable
                    .end()
                .assertFetchResult(OperationResultStatusType.FATAL_ERROR, "Exception when translating", "WRONG");
                // (maybe it's not necessary to provide the unconvertible value in the message - reconsider)

        // name is now derived from UID
        PrismObject<ShadowType> unstorableAfter = findShadowByPrismName(UNSTORABLE_ACCOUNT_UID,
                RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID.object, result);
        assertShadow(unstorableAfter, UNSTORABLE_ACCOUNT)
                .display()
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(UNSTORABLE_ACCOUNT_UID)
                .attributes()
                    .assertSize(1)
                    .end();

        assertSelectedAccountByName(objects, TOTALLY_UNSTORABLE_ACCOUNT) // it has name, because the name attribute was not removed (why?)
                .assertNoOid()
                // Primary identifier value is not here, because it is set as part of object shadowization (which failed)
                .attributes()
                    .assertSize(3) // number, name, uid
                    .end()
                .assertFetchResult(OperationResultStatusType.FATAL_ERROR, "could not execute batch");
    }

    @Test
    public void test270LiveSyncBrokenAccountsExternalUid() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        cleanupAccounts(RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID, result);
        RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID.controller.setSyncStyle(DummySyncStyle.SMART);

        ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID.oid,
                SchemaConstants.RI_ACCOUNT_OBJECT_CLASS);

        List<LiveSyncEvent> events = new ArrayList<>();
        LiveSyncEventHandler handler = new LiveSyncEventHandler() {
            @Override
            public void allEventsSubmitted(OperationResult result) {
            }

            @Override
            public boolean handle(LiveSyncEvent event, OperationResult opResult) {
                events.add(event);
                event.acknowledge(true, opResult);
                return true;
            }
        };
        provisioningService.synchronize(coords, task, null, handler, result);
        assertThat(events).isEmpty();

        createAccountExternalUid(GOOD_ACCOUNT, 1, null);
        createAccountExternalUid(INCONVERTIBLE_ACCOUNT, 2, "WRONG");
        createAccountExternalUid(UNSTORABLE_ACCOUNT, "WRONG", null);
        createAccountExternalUid(TOTALLY_UNSTORABLE_ACCOUNT, 4, null);

        when();

        provisioningService.synchronize(coords, task, null, handler, result);

        then();
        display("events", events);
        assertThat(events.size()).as("events found").isEqualTo(4);

        List<PrismObject<ShadowType>> objects = events.stream()
                .filter(event -> event.getChangeDescription() != null)
                .map(event -> event.getChangeDescription().getShadowedResourceObject())
                .collect(Collectors.toList());

        assertSelectedAccountByName(objects, GOOD_ACCOUNT)
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(GOOD_ACCOUNT_UID)
                .assertName(GOOD_ACCOUNT)
                .attributes()
                    .assertSize(3)
                    .end()
                .assertSuccessOrNoFetchResult();

        PrismObject<ShadowType> goodAfter = findShadowByPrismName(GOOD_ACCOUNT, RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID.object, result);
        assertShadow(goodAfter, GOOD_ACCOUNT)
                .display()
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(GOOD_ACCOUNT_UID)
                .attributes()
                    .assertSize(3)
                    .end();

        assertSelectedAccountByName(objects, INCONVERTIBLE_ACCOUNT_UID)
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(INCONVERTIBLE_ACCOUNT_UID)
                .assertName(INCONVERTIBLE_ACCOUNT_UID)
                .attributes()
                    .assertSize(1) // uid=uid:inconvertible (for some reason number=2 is not there)
                    .end();

        // name is now derived from UID
        PrismObject<ShadowType> inconvertibleAfter = findShadowByPrismName(INCONVERTIBLE_ACCOUNT_UID,
                RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID.object, result);
        assertShadow(inconvertibleAfter, INCONVERTIBLE_ACCOUNT)
                .display()
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(INCONVERTIBLE_ACCOUNT_UID)
                .attributes()
                    .assertSize(1)
                    .end();

        assertSelectedAccountByName(objects, UNSTORABLE_ACCOUNT_UID)
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(UNSTORABLE_ACCOUNT_UID)
                .assertName(UNSTORABLE_ACCOUNT_UID)
                .attributes()
                    .assertSize(1) // uid=unstorable
                    .end();

        // name is now derived from UID
        PrismObject<ShadowType> unstorableAfter = findShadowByPrismName(UNSTORABLE_ACCOUNT_UID,
                RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID.object, result);
        assertShadow(unstorableAfter, UNSTORABLE_ACCOUNT)
                .display()
                .assertOid()
                .assertKind(ShadowKindType.ACCOUNT)
                .assertPrimaryIdentifierValue(UNSTORABLE_ACCOUNT_UID)
                .attributes()
                    .assertSize(1)
                    .end();

        // The fetch result is not in the shadows. The exception is recorded in events.

        List<LiveSyncEvent> noChangeEvents = events.stream()
                .filter(event -> event.getChangeDescription() == null)
                .collect(Collectors.toList());
        assertThat(noChangeEvents).hasSize(1);
        LiveSyncEvent failedEvent = noChangeEvents.get(0);
        displayDumpable("failed event", failedEvent);
        assertThat(failedEvent.isError()).isTrue();
    }

    private void createAccount(String name, Object number, Object enableDate) throws Exception {
        DummyAccount account = RESOURCE_DUMMY_BROKEN_ACCOUNTS.controller.addAccount(name);
        account.addAttributeValue(ATTR_NUMBER, number);
        account.addAttributeValue(ENABLE_DATE_NAME, enableDate);
    }

    private void createAccountExternalUid(String name, Object number, Object enableDate) throws Exception {
        DummyAccount account = new DummyAccount(name);
        account.setId(EXTERNAL_UID_PREFIX + name);
        account.addAttributeValue(ATTR_NUMBER, number);
        account.addAttributeValue(ENABLE_DATE_NAME, enableDate);
        RESOURCE_DUMMY_BROKEN_ACCOUNTS_EXTERNAL_UID.controller.getDummyResource().addAccount(account);
    }
    //endregion
}
