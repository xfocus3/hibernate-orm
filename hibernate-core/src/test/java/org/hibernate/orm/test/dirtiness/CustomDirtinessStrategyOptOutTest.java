package org.hibernate.orm.test.dirtiness;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.CustomEntityDirtinessStrategy;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JiraKey("HHH-15611")
@DomainModel(annotatedClasses = {
		CustomDirtinessStrategyOptOutTest.TrackedEntity.class,
		CustomDirtinessStrategyOptOutTest.UntrackedEntity.class
})
@ServiceRegistry(settings = @Setting(
		name = AvailableSettings.CUSTOM_ENTITY_DIRTINESS_STRATEGY,
		value = "org.hibernate.orm.test.dirtiness.CustomDirtinessStrategyOptOutTest$Strategy"
))
@SessionFactory(generateStatistics = true)
class CustomDirtinessStrategyOptOutTest {
	@BeforeEach
	void prepareData(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			session.createMutationQuery( "delete TrackedEntity" ).executeUpdate();
			session.createMutationQuery( "delete UntrackedEntity" ).executeUpdate();
			session.createNativeMutationQuery( "insert into TrackedEntity (id, name) values (1, 'initial')" )
					.executeUpdate();
			session.createNativeMutationQuery( "insert into UntrackedEntity (id, name) values (1, 'initial')" )
					.executeUpdate();
		} );
		strategy( scope ).callbacks.clear();
		strategy( scope ).checkAttributes = true;
		scope.getSessionFactory().getStatistics().clear();
	}

	@Test
	void testPersistUnsupportedEntity(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final var entity = new UntrackedEntity();
			entity.id = 2L;
			entity.name = "new";
			session.persist( entity );
			session.flush();
			assertNoCallbacks( scope );
		} );
		scope.inTransaction( session -> assertEquals( "new", session.find( UntrackedEntity.class, 2L ).name ) );
		assertEquals( 1, scope.getSessionFactory().getStatistics().getEntityInsertCount() );
		assertEquals( 0, scope.getSessionFactory().getStatistics().getEntityUpdateCount() );
	}

	@Test
	void testLoadUnsupportedEntity(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			assertEquals( "initial", session.find( UntrackedEntity.class, 1L ).name );
			assertNoCallbacks( scope );
		} );
	}

	@Test
	void testUpdateUnsupportedEntity(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final var entity = session.find( UntrackedEntity.class, 1L );
			strategy( scope ).callbacks.clear();
			entity.name = "updated";
			session.flush();
			assertNoCallbacks( scope );
			assertEquals( 1, scope.getSessionFactory().getStatistics().getEntityUpdateCount() );
			session.flush();
			assertNoCallbacks( scope );
			assertEquals( 1, scope.getSessionFactory().getStatistics().getEntityUpdateCount() );
		} );
		scope.inTransaction( session -> assertEquals( "updated", session.find( UntrackedEntity.class, 1L ).name ) );
	}

	@Test
	void testFlushUnchangedUnsupportedEntity(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			session.find( UntrackedEntity.class, 1L );
			strategy( scope ).callbacks.clear();
			session.flush();
			assertNoCallbacks( scope );
			assertEquals( 0, scope.getSessionFactory().getStatistics().getEntityUpdateCount() );
		} );
	}

	@Test
	void testMixedSupportedAndUnsupportedEntities(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final var tracked = session.find( TrackedEntity.class, 1L );
			final var untracked = session.find( UntrackedEntity.class, 1L );
			strategy( scope ).callbacks.clear();
			tracked.setName( "tracked update" );
			untracked.name = "untracked update";
			session.flush();
			assertEquals(
					List.of( "isDirty", "findDirty", "resetDirty" ),
					strategy( scope ).callbacks,
					"Unsupported entities must not receive strategy callbacks"
			);
			assertTrue( tracked.changes.isEmpty() );
			assertEquals( 2, scope.getSessionFactory().getStatistics().getEntityUpdateCount() );
		} );
		scope.inTransaction( session -> {
			assertEquals( "tracked update", session.find( TrackedEntity.class, 1L ).getName() );
			assertEquals( "untracked update", session.find( UntrackedEntity.class, 1L ).name );
		} );
	}

	@Test
	void testSupportedEntityWithoutDirtyAttributes(SessionFactoryScope scope) {
		strategy( scope ).checkAttributes = false;
		scope.inTransaction( session -> {
			final var entity = session.find( TrackedEntity.class, 1L );
			entity.setName( "initial" );
			session.flush();
			assertEquals( List.of( "resetDirty", "isDirty", "findDirty", "resetDirty" ), strategy( scope ).callbacks );
			assertTrue( entity.changes.isEmpty() );
			assertEquals( 0, scope.getSessionFactory().getStatistics().getEntityUpdateCount() );
		} );
	}

	@Test
	void testSupportedEntityLifecycle(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final var entity = session.find( TrackedEntity.class, 1L );
			assertTrue( entity.changes.isEmpty() );
			assertEquals( List.of( "resetDirty" ), strategy( scope ).callbacks );
			entity.setName( "updated" );
			session.flush();
			assertEquals( List.of( "resetDirty", "isDirty", "findDirty", "resetDirty" ), strategy( scope ).callbacks );
			assertTrue( entity.changes.isEmpty() );
			assertEquals( 1, scope.getSessionFactory().getStatistics().getEntityUpdateCount() );
			session.flush();
			assertEquals(
					List.of( "resetDirty", "isDirty", "findDirty", "resetDirty", "isDirty" ),
					strategy( scope ).callbacks
			);
			assertEquals( 1, scope.getSessionFactory().getStatistics().getEntityUpdateCount() );
		} );
		scope.inTransaction( session -> assertEquals( "updated", session.find( TrackedEntity.class, 1L ).getName() ) );
	}

	@Test
	void testSupportedEntityCanDelegateAttributeChecking(SessionFactoryScope scope) {
		strategy( scope ).checkAttributes = false;
		scope.inTransaction( session -> {
			final var entity = session.find( TrackedEntity.class, 1L );
			entity.setName( "updated" );
			session.flush();
			assertTrue( entity.changes.isEmpty() );
			assertEquals( List.of( "resetDirty", "isDirty", "findDirty", "resetDirty" ), strategy( scope ).callbacks );
		} );
		scope.inTransaction( session -> assertEquals( "updated", session.find( TrackedEntity.class, 1L ).getName() ) );
	}

	@Test
	void testSupportedEntityCanSuppressUpdate(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final var entity = session.find( TrackedEntity.class, 1L );
			entity.name = "untracked change";
			session.flush();
			assertEquals( List.of( "resetDirty", "isDirty" ), strategy( scope ).callbacks );
			assertEquals( 0, scope.getSessionFactory().getStatistics().getEntityUpdateCount() );
		} );
		scope.inTransaction( session -> assertEquals( "initial", session.find( TrackedEntity.class, 1L ).getName() ) );
	}

	private static Strategy strategy(SessionFactoryScope scope) {
		return (Strategy) scope.getSessionFactory().getCustomEntityDirtinessStrategy();
	}

	private static void assertNoCallbacks(SessionFactoryScope scope) {
		assertEquals( List.of(), strategy( scope ).callbacks, "Unsupported entities must not receive strategy callbacks" );
	}

	@Entity(name = "UntrackedEntity")
	static class UntrackedEntity {
		@Id
		Long id;
		String name;
	}

	@Entity(name = "TrackedEntity")
	static class TrackedEntity {
		@Id
		Long id;
		@Access(AccessType.PROPERTY)
		String name;
		@Transient
		final Set<String> changes = new HashSet<>();

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
			changes.add( "name" );
		}
	}

	// Record unsupported callbacks without throwing so each lifecycle phase can be checked independently.
	public static class Strategy implements CustomEntityDirtinessStrategy {
		final List<String> callbacks = new ArrayList<>();
		boolean checkAttributes = true;

		private void recordCallback(String name, Object entity) {
			callbacks.add( entity instanceof TrackedEntity ? name : "unsupported:" + name );
		}

		@Override
		public boolean canDirtyCheck(Object entity, EntityPersister persister, Session session) {
			return entity instanceof TrackedEntity;
		}

		@Override
		public boolean isDirty(Object entity, EntityPersister persister, Session session) {
			recordCallback( "isDirty", entity );
			return entity instanceof TrackedEntity tracked && !tracked.changes.isEmpty();
		}

		@Override
		public void resetDirty(Object entity, EntityPersister persister, Session session) {
			recordCallback( "resetDirty", entity );
			if ( entity instanceof TrackedEntity tracked ) {
				tracked.changes.clear();
			}
		}

		@Override
		public void findDirty(Object entity, EntityPersister persister, Session session, DirtyCheckContext context) {
			recordCallback( "findDirty", entity );
			if ( checkAttributes && entity instanceof TrackedEntity tracked ) {
				context.doDirtyChecking( attribute -> tracked.changes.contains( attribute.getName() ) );
			}
		}
	}
}
