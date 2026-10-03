package org.hibernate.orm.test.query;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.hibernate.jpa.spi.NativeQueryListTransformer;
import org.hibernate.sql.results.internal.RowTransformerListImpl;

import org.hibernate.testing.orm.junit.BaseUnitTest;
import org.hibernate.testing.orm.junit.JiraKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@BaseUnitTest
@JiraKey( "HHH-20328" )
class ListResultTransformerTest {
	@ParameterizedTest(name = "null values: {0}")
	@MethodSource("transformers")
	void testNullValues(String name, Function<Object[], List<Object>> transformer) {
		Object[] row = { null, 1, null, "value", null };
		assertEquals( Arrays.asList( row ), transformer.apply( row ) );
	}

	@ParameterizedTest(name = "unmodifiable copy: {0}")
	@MethodSource("transformers")
	void testUnmodifiableCopy(String name, Function<Object[], List<Object>> transformer) {
		Object[] row = { 1, "value" };
		List<Object> result = transformer.apply( row );
		row[0] = 2;
		assertEquals( List.of( 1, "value" ), result );
		assertThrows( UnsupportedOperationException.class, () -> result.set( 0, 3 ) );
		assertThrows( UnsupportedOperationException.class, () -> result.add( 3 ) );
	}

	private static Stream<Arguments> transformers() {
		Function<Object[], List<Object>> hql = RowTransformerListImpl.instance()::transformRow;
		Function<Object[], List<Object>> nativeSql =
				row -> NativeQueryListTransformer.INSTANCE.transformTuple( row, new String[row.length] );
		return Stream.of( Arguments.of( "HQL", hql ), Arguments.of( "native SQL", nativeSql ) );
	}
}
