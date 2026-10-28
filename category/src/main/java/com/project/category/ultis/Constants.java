package com.project.category.ultis;

public interface Constants {
    public interface ENDPOINT {
        public final static String PREFIX_CATEGORY = "/api/category";
        /**
         * Danh mục khác đặt dưới /api/category/catalogs/&lt;tên&gt; để không trùng GET /api/category/{code}.
         * Gateway và public-paths (GET /api/category/**) đã bao gồm sẵn.
         */
        public final static String PREFIX_CATALOG = "/api/category/catalogs";
        public final static String PREFIX_UNIT = PREFIX_CATALOG + "/units";

    }
}
