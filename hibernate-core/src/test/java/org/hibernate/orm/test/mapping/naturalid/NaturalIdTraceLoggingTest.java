package org.hibernate.orm.test.mapping.naturalid;

import org.hibernate.Hibernate;
import org.hibernate.annotations.NaturalId;
import org.hibernate.pretty.MessageHelper;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.Jira;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DomainModel(annotatedClasses = {
		NaturalIdTraceLoggingTest.School.class,
		NaturalIdTraceLoggingTest.SchoolRecord.class,
		NaturalIdTraceLoggingTest.ArrayRecord.class
})
@SessionFactory
@Jira("HHH-20448")
class NaturalIdTraceLoggingTest {
	@Test
	void rendersAssociationWithoutInitializingProxy(SessionFactoryScope scope) {
		scope.inSession( session -> {
			final var factory = scope.getSessionFactory();
			final var metamodel = factory.getMappingMetamodel();
			final var schoolRecord = metamodel.getEntityDescriptor( SchoolRecord.class );
			final String schoolName = metamodel.getEntityDescriptor( School.class ).getEntityName();
			final int schoolId = 1;
			final School school = session.getReference( School.class, schoolId );

			assertFalse( Hibernate.isInitialized( school ) );
			assertEquals( schoolName + "#" + schoolId,
					MessageHelper.naturalIdToLoggableString( school, schoolRecord, factory ) );
			assertFalse( Hibernate.isInitialized( school ) );
		} );
	}

	@Test
	void distinguishesWrappedScalarFromSingleElementArray(SessionFactoryScope scope) {
		final var factory = scope.getSessionFactory();
		final var metamodel = factory.getMappingMetamodel();

		assertEquals( "school", MessageHelper.naturalIdToLoggableString(
				new Object[] { "school" }, metamodel.getEntityDescriptor( School.class ), factory ) );
		assertEquals( "[alpha]", MessageHelper.naturalIdToLoggableString(
				new String[] { "alpha" }, metamodel.getEntityDescriptor( ArrayRecord.class ), factory ) );
	}

	@Entity(name = "School")
	static class School {
		@Id
		private Integer id;

		@NaturalId
		private String name;
	}

	@Entity(name = "SchoolRecord")
	static class SchoolRecord {
		@Id
		private Integer id;

		@ManyToOne
		@NaturalId
		private School school;
	}

	@Entity(name = "ArrayRecord")
	static class ArrayRecord {
		@Id
		private Integer id;

		@NaturalId
		private String[] labels;
	}
}
