import os, glob, re

d = r'c:\Users\vedan\ai-finance-assistant\finance-frontend\src'
files = glob.glob(d + '/**/*.tsx', recursive=True)

for f in files:
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    
    orig = content
    
    # 1. Remove trailing comma if user?.token was at end
    content = re.sub(r',\s*user\?\.token', '', content)
    # 2. Remove leading comma if user?.token was at start/middle
    content = re.sub(r'user\?\.token,\s*', '', content)
    # 3. Remove standalone user?.token in arrays
    content = re.sub(r'\[user\?\.token\]', '[]', content)
    
    # 4. Remove standalone Auth headers obj
    content = re.sub(r'const headers = \{ Authorization: `Bearer \$\{user\?\.token\}` \}\s*', '', content)
    
    # 5. Remove inline Auth headers obj
    content = re.sub(r'\{\s*headers:\s*\{\s*Authorization:\s*`Bearer \$\{user\?\.token\}`\s*\}\s*\}', '{}', content)
    
    # 6. Fix unused var in StatementImport
    if 'StatementImport.tsx' in f:
        content = content.replace('const consentUrl = consentRes.data.consentUrl', 'const consentUrl = consentRes.data.consentUrl; console.log(consentUrl)')

    if orig != content:
        with open(f, 'w', encoding='utf-8') as out:
            out.write(content)
        print("Updated " + f)
