package com.gamechronicle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapperTest {
 @Test void allMapperStatementsParse(){var c=new org.apache.ibatis.session.Configuration();c.getTypeHandlerRegistry().register(UuidTypeHandler.class);c.addMapper(Store.class);assertTrue(c.hasStatement("com.gamechronicle.Store.claim"));assertTrue(c.hasStatement("com.gamechronicle.Store.sessions"));}
}
