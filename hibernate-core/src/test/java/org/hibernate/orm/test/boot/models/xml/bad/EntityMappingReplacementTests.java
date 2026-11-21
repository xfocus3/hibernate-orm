package org.hibernate.orm.test.boot.models.xml.bad;

import org.hibernate.boot.MappingException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.orm.test.boot.models.xml.SimpleEntity;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;
import org.junit.jupiter.api.Test;

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
		assertReplacementRejected( scope, "complete-same-name" );
	}

	@Test
	void testUnnamedCompleteMappingCannotReplaceEarlierMapping(ServiceRegistryScope scope) {
		assertReplacementRejected( scope, "complete-unnamed" );
	}

	@Test
	void testPersistenceUnitCompletenessAppliesToEveryDocument(ServiceRegistryScope scope) {
		assertReplacementRejected( scope, "global-complete" );
	}

	private static void assertMergedMapping(ServiceRegistryScope scope, String mapping, String entityName) {
		final var metadata = sources( scope, mapping ).buildMetadata();
		assertEquals( 1, metadata.getEntityBindings().size() );
		final var entity = metadata.getEntityBinding( SimpleEntity.class.getName() );
		assertEquals( entityName, entity.getJpaEntityName() );
		assertEquals( "original_id", entity.getIdentifier().getColumns().get( 0 ).getName() );
		assertEquals( "merged_name", entity.getProperty( "name" ).getValue().getColumns().get( 0 ).getName() );
	}

	private static void assertReplacementRejected(ServiceRegistryScope scope, String mapping) {
		final var exception = assertThrows( MappingException.class, () -> sources( scope, mapping ).buildMetadata() );
		assertEquals( resource( mapping ), exception.getOrigin().getName() );
		assertTrue( exception.getMessage().contains( SimpleEntity.class.getName() ) );
		assertTrue( exception.getMessage().contains( "OriginalEntity" ) );
	}

	private static MetadataSources sources(ServiceRegistryScope scope, String mapping) {
		return new MetadataSources( scope.getRegistry() )
				.addResource( resource( mapping.startsWith( "complete-" ) ? "first-complete" : "first" ) )
				.addResource( resource( mapping ) );
	}

	private static String resource(String mapping) {
		return "mappings/models/bad/entity-replacement-" + mapping + ".xml";
	}
}
