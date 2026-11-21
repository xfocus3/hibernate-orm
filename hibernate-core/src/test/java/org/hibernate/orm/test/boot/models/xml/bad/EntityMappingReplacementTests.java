package org.hibernate.orm.test.boot.models.xml.bad;

import org.hibernate.boot.MappingException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.archive.internal.ByteArrayInputStreamAccess;
import org.hibernate.orm.test.boot.models.xml.SimpleEntity;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ServiceRegistry
public class EntityMappingReplacementTests {
	@Test
	void testPartialMappingCanRenameEntity(ServiceRegistryScope scope) {
		assertMergedMapping( scope, "partial-renamed", "MergedEntity" );
	}

	@Test
	void testUnnamedPartialMappingRetainsEarlierName(ServiceRegistryScope scope) {
		assertMergedMapping( scope, "partial-unnamed", "OriginalEntity" );
	}

	@Test
	void testIdenticalCompleteMappingsInSeparateResources(ServiceRegistryScope scope) {
		final var metadata = new MetadataSources( scope.getRegistry() )
				.addResource( resource( "first-complete" ) )
				.addResource( resource( "first-complete-copy" ) )
				.buildMetadata();
		assertEquals( 1, metadata.getEntityBindings().size() );
		final var entity = metadata.getEntityBinding( SimpleEntity.class.getName() );
		assertEquals( "OriginalEntity", entity.getJpaEntityName() );
		assertEquals( "original_id", entity.getIdentifier().getColumns().get( 0 ).getName() );
		assertEquals( "original_name", entity.getProperty( "name" ).getValue().getColumns().get( 0 ).getName() );
	}

	@Test
	void testCompleteMappingCannotReplaceEarlierMappingWithSameName(ServiceRegistryScope scope) {
		assertReplacementRejected( scope, "complete-same-name", "OriginalEntity", ", removed basic attribute 'name'" );
	}

	@Test
	void testUnnamedCompleteMappingCannotReplaceEarlierMapping(ServiceRegistryScope scope) {
		assertReplacementRejected( scope, "complete-unnamed", "SimpleEntity", "" );
	}

	@Test
	void testPersistenceUnitCompletenessAppliesToEveryDocument(ServiceRegistryScope scope) {
		assertReplacementRejected( scope, "global-complete", "OriginalEntity", ", removed basic attribute 'name'" );
	}

	@Test
	void testEquivalentUnnamedCompleteMappingDoesNotForgetExplicitName(ServiceRegistryScope scope) {
		final String basic = "<basic name=\"name\"/>";
		final var named = completeMapping( "named.xml", "name=\"SimpleEntity\"", basic );
		final var unnamed = completeMapping( "equivalent-unnamed.xml", "", basic );
		final var sources = new MetadataSources( scope.getRegistry() ).addInputStream( named ).addInputStream( unnamed );
		final var entity = sources.buildMetadata().getEntityBinding( SimpleEntity.class.getName() );
		assertEquals( "SimpleEntity", entity.getJpaEntityName() );
		assertCompleteReplacementRejected(
				new MetadataSources( scope.getRegistry() ).addInputStream( named ).addInputStream( unnamed ),
				completeMapping( "renamed.xml", "name=\"Renamed\"", basic ),
				"entity names 'SimpleEntity' and 'Renamed'"
		);
	}

	@Test
	void testCompleteMappingCannotRemoveNaturalIdBasicAttribute(ServiceRegistryScope scope) {
		final var naturalId = completeMapping(
				"natural-id.xml",
				"name=\"OriginalEntity\"",
				"<natural-id><basic name=\"name\"/></natural-id>"
		);
		final var sources = new MetadataSources( scope.getRegistry() ).addInputStream( naturalId );
		final var entity = sources.buildMetadata().getEntityBinding( SimpleEntity.class.getName() );
		assertTrue( entity.getProperty( "name" ).isNaturalIdentifier() );
		assertCompleteReplacementRejected(
				new MetadataSources( scope.getRegistry() ).addInputStream( naturalId ),
				completeMapping( "transient-natural-id.xml", "name=\"OriginalEntity\"", "<transient name=\"name\"/>" ),
				"removed basic attribute 'name'"
		);
	}

	@Test
	void testCompleteMappingCannotRemoveImplicitFieldBasicAttribute(ServiceRegistryScope scope) {
		final var implicit = completeMapping( "implicit.xml", "name=\"OriginalEntity\"", "" );
		final var sources = new MetadataSources( scope.getRegistry() ).addInputStream( implicit );
		final var entity = sources.buildMetadata().getEntityBinding( SimpleEntity.class.getName() );
		assertEquals( "name", entity.getProperty( "name" ).getName() );
		assertCompleteReplacementRejected(
				new MetadataSources( scope.getRegistry() ).addInputStream( implicit ),
				completeMapping( "transient-implicit.xml", "name=\"OriginalEntity\"", "<transient name=\"name\"/>" ),
				"removed basic attribute 'name'"
		);
	}

	private static void assertCompleteReplacementRejected(
			MetadataSources sources,
			ByteArrayInputStreamAccess incoming,
			String diagnostic) {
		sources.addInputStream( incoming );
		final var exception = assertThrows( MappingException.class, sources::buildMetadata );
		assertEquals( incoming.getStreamName(), exception.getOrigin().getName() );
		assertTrue( exception.getMessage().contains( SimpleEntity.class.getName() ) );
		assertTrue( exception.getMessage().contains( diagnostic ) );
	}

	private static ByteArrayInputStreamAccess completeMapping(
			String origin,
			String entityNameAttribute,
			String additionalAttributes) {
		final String mapping = """
				<entity-mappings xmlns="http://www.hibernate.org/xsd/orm/mapping" version="7.0">
					<package>org.hibernate.orm.test.boot.models.xml</package>
					<entity class="SimpleEntity" %s metadata-complete="true" access="FIELD">
						<attributes>
							<id name="id"/>
							%s
						</attributes>
					</entity>
				</entity-mappings>
				""".formatted( entityNameAttribute, additionalAttributes );
		return new ByteArrayInputStreamAccess( origin, mapping.getBytes( UTF_8 ) );
	}

	private static void assertMergedMapping(ServiceRegistryScope scope, String mapping, String entityName) {
		final var metadata = sources( scope, mapping ).buildMetadata();
		assertEquals( 1, metadata.getEntityBindings().size() );
		final var entity = metadata.getEntityBinding( SimpleEntity.class.getName() );
		assertEquals( entityName, entity.getJpaEntityName() );
		assertEquals( "original_id", entity.getIdentifier().getColumns().get( 0 ).getName() );
		assertEquals( "merged_name", entity.getProperty( "name" ).getValue().getColumns().get( 0 ).getName() );
	}

	private static void assertReplacementRejected(
			ServiceRegistryScope scope,
			String mapping,
			String incomingName,
			String attributeDetail) {
		final var exception = assertThrows( MappingException.class, () -> sources( scope, mapping ).buildMetadata() );
		assertEquals( resource( mapping ), exception.getOrigin().getName() );
		assertEquals(
				"Conflicting complete XML mappings for entity class '" + SimpleEntity.class.getName()
						+ "': entity names 'OriginalEntity' and '" + incomingName
						+ "'" + attributeDetail + " [" + resource( mapping ) + "]",
				exception.getMessage()
		);
	}

	private static MetadataSources sources(ServiceRegistryScope scope, String mapping) {
		final String firstMapping = mapping.startsWith( "partial-" ) ? "first" : "first-complete";
		return new MetadataSources( scope.getRegistry() )
				.addResource( resource( firstMapping ) )
				.addResource( resource( mapping ) );
	}

	private static String resource(String mapping) {
		return "mappings/models/bad/entity-replacement-" + mapping + ".xml";
	}
}
