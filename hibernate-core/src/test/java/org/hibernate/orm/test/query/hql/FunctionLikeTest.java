package org.hibernate.orm.test.query.hql;

import java.util.List;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.query.SemanticException;

import org.hibernate.testing.orm.domain.gambit.BasicEntity;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DomainModel(annotatedClasses = BasicEntity.class)
@SessionFactory
@JiraKey("HHH-19875")
public class FunctionLikeTest {
	@BeforeEach
	public void prepareData(SessionFactoryScope scope) {
		// The return type registered for substring() must also be used by FUNCTION().
		assertNotNull( scope.getSessionFactory().getQueryEngine().getSqmFunctionRegistry()
				.findFunctionDescriptor( "substring" ) );
		scope.inTransaction( session -> {
			session.persist( new BasicEntity( 1, "abc_one" ) );
			session.persist( new BasicEntity( 2, "abcTwo" ) );
			session.persist( new BasicEntity( 3, "xyz" ) );
			session.persist( new BasicEntity( 4, null ) );
		} );
	}

	@AfterEach
	public void tearDown(SessionFactoryScope scope) {
		scope.dropData();
	}

	@Test
	@RequiresDialect(H2Dialect.class)
	public void testDialectRegisteredFunction(SessionFactoryScope scope) {
		assertMatches( scope, "function('substr', e.data, 1, 3) like 'abc'", List.of( 1, 2 ) );
		assertMatches( scope, "'abc' like function('substr', e.data, 1, 3)", List.of( 1, 2 ) );
	}

	@Test
	public void testFunctionAsMatchExpression(SessionFactoryScope scope) {
		assertMatches( scope, "function('substring', e.data, 1, 3) like 'abc'", List.of( 1, 2 ) );
	}

	@Test
	public void testFunctionAsPattern(SessionFactoryScope scope) {
		assertMatches( scope, "'abc' like function('substring', e.data, 1, 3)", List.of( 1, 2 ) );
	}

	@Test
	public void testFunctionsAsBothOperands(SessionFactoryScope scope) {
		assertMatches( scope,
				"function('substring', e.data, 1, 3) like function('substring', 'abcdef', 1, 3)",
				List.of( 1, 2 ) );
	}

	@Test
	public void testPatternParameter(SessionFactoryScope scope) {
		assertParameterMatches( scope, "function('substring', e.data, 1, 3) like :value" );
	}

	@Test
	public void testMatchParameter(SessionFactoryScope scope) {
		assertParameterMatches( scope, ":value like function('substring', e.data, 1, 3)" );
	}

	@Test
	public void testNotLike(SessionFactoryScope scope) {
		assertMatches( scope, "function('substring', e.data, 1, 3) not like 'abc'", List.of( 3 ) );
	}

	@Test
	public void testEscape(SessionFactoryScope scope) {
		assertMatches( scope, "function('substring', e.data, 1, 4) like 'abc!_' escape '!'", List.of( 1 ) );
	}

	@Test
	public void testExplicitStringType(SessionFactoryScope scope) {
		assertMatches( scope, "function('substring' as String, e.data, 1, 3) like 'abc'", List.of( 1, 2 ) );
	}

	@Test
	public void testRegisteredStringFunction(SessionFactoryScope scope) {
		assertMatches( scope, "substring(e.data, 1, 3) like 'abc'", List.of( 1, 2 ) );
	}

	@Test
	public void testInvalidOperandsRejected(SessionFactoryScope scope) {
		scope.inSession( session -> {
			for ( String predicate : List.of(
					"e.id like '1'",
					"'1' like e.id",
					"length(e.data) like '3'",
					"'3' like length(e.data)",
					"function('length', e.data) like '3'",
					"'3' like function('length', e.data)",
					"function('sum', e.id) like '1'",
					"'1' like function('sum', e.id)",
					"function('substring' as Integer, e.data, 1, 3) like 'abc'",
					"'abc' like function('substring' as Integer, e.data, 1, 3)",
					"function('unregistered_function_for_like_test', e.data) like 'abc'",
					"'abc' like function('unregistered_function_for_like_test', e.data)" ) ) {
				final var exception = assertThrows( IllegalArgumentException.class,
						() -> session.createQuery( "select e.id from BasicEntity e where " + predicate, Integer.class ),
						predicate );
				assertInstanceOf( SemanticException.class, exception.getCause() );
				assertTrue( exception.getCause().getMessage().contains( "not a string" ), predicate );
			}
		} );
	}

	private void assertMatches(SessionFactoryScope scope, String predicate, List<Integer> expected) {
		scope.inTransaction( session -> {
			final var query = assertDoesNotThrow( () -> session.createQuery(
					"select e.id from BasicEntity e where " + predicate + " order by e.id", Integer.class
			) );
			assertEquals( expected, query.getResultList() );
			assertEquals( expected.size(), query.getResultCount() );
		} );
	}

	private void assertParameterMatches(SessionFactoryScope scope, String predicate) {
		scope.inTransaction( session -> {
			final var query = assertDoesNotThrow( () -> session.createQuery(
					"select e.id from BasicEntity e where " + predicate + " order by e.id", Integer.class
			) );
			assertEquals( String.class, query.getParameter( "value" ).getParameterType() );
			assertEquals( List.of( 1, 2 ), query.setParameter( "value", "abc" ).getResultList() );
			assertEquals( 2, query.getResultCount() );
		} );
	}
}
