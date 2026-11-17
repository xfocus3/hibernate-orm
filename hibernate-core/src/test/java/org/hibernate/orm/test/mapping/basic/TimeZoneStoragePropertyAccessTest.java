package org.hibernate.orm.test.mapping.basic;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.DomainModelScope;
import org.hibernate.testing.orm.junit.Jira;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import static org.assertj.core.api.Assertions.assertThat;

@Jira("https://hibernate.atlassian.net/browse/HHH-21189")
@DomainModel(annotatedClasses = TimeZoneStoragePropertyAccessTest.PropertyAccess.class)
@SessionFactory
public class TimeZoneStoragePropertyAccessTest {
	private static final OffsetDateTime OFFSET = OffsetDateTime.parse( "2026-01-15T12:30:00+05:45" );
	private static final OffsetTime TIME = OFFSET.toOffsetTime();
	private static final ZonedDateTime ZONED = OFFSET.toZonedDateTime();

	@Test
	void columns(DomainModelScope scope) {
		scope.withHierarchy( PropertyAccess.class, entity -> {
			assertThat( entity.getTable().getColumns() ).extracting( org.hibernate.mapping.Column::getName )
					.containsExactlyInAnyOrder( "id", "zoned", "zoned_tz", "offset", "offset_tz", "time", "time_tz" );
		} );
	}

	@Test
	void roundTrip(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final PropertyAccess entity = new PropertyAccess();
			entity.setId( 1 );
			entity.setOffset( OFFSET );
			entity.setTime( TIME );
			entity.setZoned( ZONED );
			session.persist( entity );
		} );
		scope.inTransaction( session -> {
			final PropertyAccess entity = session.find( PropertyAccess.class, 1 );
			assertThat( entity.getOffset() ).isEqualTo( OFFSET );
			assertThat( entity.getTime() ).isEqualTo( TIME );
			assertThat( entity.getZoned() ).isEqualTo( ZONED );
			session.remove( entity );
		} );
	}

	@Test
	@DomainModel(annotatedClasses = { PropertyAccess.class, FieldAccess.class })
	void mixedAccess(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final FieldAccess entity = new FieldAccess();
			entity.id = 2;
			entity.offset = OFFSET;
			entity.time = TIME;
			entity.zoned = ZONED;
			session.persist( entity );
		} );
		scope.inTransaction( session -> {
			final FieldAccess entity = session.find( FieldAccess.class, 2 );
			assertThat( entity.offset ).isEqualTo( OFFSET );
			assertThat( entity.time ).isEqualTo( TIME );
			assertThat( entity.zoned ).isEqualTo( ZONED );
			session.remove( entity );
		} );
	}

	@Entity(name = "PropertyAccess")
	public static class PropertyAccess {
		private Integer id;
		private ZonedDateTime zoned;
		private OffsetDateTime offset;
		private OffsetTime time;

		@Id
		public Integer getId() { return id; }
		public void setId(Integer id) { this.id = id; }

		@TimeZoneStorage(TimeZoneStorageType.COLUMN)
		public ZonedDateTime getZoned() { return zoned; }
		public void setZoned(ZonedDateTime zoned) { this.zoned = zoned; }

		@TimeZoneStorage(TimeZoneStorageType.COLUMN)
		public OffsetDateTime getOffset() { return offset; }
		public void setOffset(OffsetDateTime offset) { this.offset = offset; }

		@TimeZoneStorage(TimeZoneStorageType.COLUMN)
		public OffsetTime getTime() { return time; }
		public void setTime(OffsetTime time) { this.time = time; }
	}

	@Entity(name = "FieldAccess")
	public static class FieldAccess {
		@Id
		private Integer id;
		@TimeZoneStorage(TimeZoneStorageType.COLUMN)
		private ZonedDateTime zoned;
		@TimeZoneStorage(TimeZoneStorageType.COLUMN)
		private OffsetDateTime offset;
		@TimeZoneStorage(TimeZoneStorageType.COLUMN)
		private OffsetTime time;
	}
}
