import os
fp = os.path.join(r"d:\Codes\aryn-mall\aryn-mall-java", "aryn-promotion", "aryn-promotion-biz", "src", "main", "resources", "sql", "distribution_init.sql")
os.makedirs(os.path.dirname(fp), exist_ok=True)
# Will write SQL content
print("Script created at:", fp)
