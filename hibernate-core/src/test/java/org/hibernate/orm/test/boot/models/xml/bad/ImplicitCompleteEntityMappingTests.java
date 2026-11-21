package org.hibernate.orm.test.boot.models.xml.bad;

import org.hibernate.boot.MappingException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.archive.internal.ByteArrayInputStreamAccess;
import org.hibernate.mapping.BasicValue;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ServiceRegistry
public class ImplicitCompleteEntityMappingTests {
	@Test
	void testCompleteMappingCannotRemoveImplicitPropertyBasicAttribute(ServiceRegistryScope scope) {
		final var previous = mapping( "implicit-property.xml", "PROPERTY", "" );
		final var metadata = new MetadataSources( scope.getRegistry() ).addInputStream( previous ).buildMetadata();
		final var entity = metadata.getEntityBinding( PropertyAccessEntity.class.getName() );
		assertInstanceOf( BasicValue.class, entity.getProperty( "name" ).getValue() );

		final var incoming = mapping( "transient-property.xml", "PROPERTY", "<transient name='name'/>" );
		final var sources = new MetadataSources( scope.getRegistry() ).addInputStream( previous ).addInputStream( incoming );
		final var exception = assertThrows( MappingException.class, sources::buildMetadata );
		assertEquals( incoming.getStreamName(), exception.getOrigin().getName() );
		assertTrue( exception.getMessage().contains( PropertyAccessEntity.class.getName() ) );
		assertTrue( exception.getMessage().contains( "removed basic attribute 'name'" ) );
	}

	@Test
	void testEquivalentCompleteMappingsCanBothDeclareAttributeTransient(ServiceRegistryScope scope) {
		final String attribute = "<transient name='name'/>";
		final var metadata = new MetadataSources( scope.getRegistry() )
				.addInputStream( mapping( "transient.xml", "FIELD", attribute ) )
				.addInputStream( mapping( "transient-copy.xml", "FIELD", attribute ) )
				.buildMetadata();
		final var entity = metadata.getEntityBinding( PropertyAccessEntity.class.getName() );
		assertEquals( "OriginalEntity", entity.getJpaEntityName() );
		assertFalse( entity.getProperties().stream().anyMatch( property -> "name".equals( property.getName() ) ) );
	}

	private static ByteArrayInputStreamAccess mapping(String origin, String access, String attributes) {
		final String mapping = "<entity-mappings xmlns='http://www.hibernate.org/xsd/orm/mapping' version='7.0'>"
				+ "<entity class='" + PropertyAccessEntity.class.getName() + "' name='OriginalEntity'"
				+ " metadata-complete='true' access='" + access + "'>"
				+ "<attributes><id name='id'/>" + attributes + "</attributes>"
				+ "</entity></entity-mappings>";
		return new ByteArrayInputStreamAccess( origin, mapping.getBytes( UTF_8 ) );
	}

	public static class PropertyAccessEntity {
		private Integer id;
		private String name;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
