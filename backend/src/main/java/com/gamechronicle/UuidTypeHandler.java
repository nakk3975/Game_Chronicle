package com.gamechronicle;
import org.apache.ibatis.type.*;
import java.sql.*;
import java.util.UUID;
@MappedTypes(UUID.class) @MappedJdbcTypes(value=JdbcType.OTHER,includeNullJdbcType=true)
public class UuidTypeHandler extends BaseTypeHandler<UUID>{
 public void setNonNullParameter(PreparedStatement ps,int i,UUID v,JdbcType t)throws SQLException{ps.setObject(i,v);}
 public UUID getNullableResult(ResultSet rs,String c)throws SQLException{return (UUID)rs.getObject(c);}
 public UUID getNullableResult(ResultSet rs,int c)throws SQLException{return (UUID)rs.getObject(c);}
 public UUID getNullableResult(CallableStatement s,int c)throws SQLException{return (UUID)s.getObject(c);}
}
