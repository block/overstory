#!/usr/bin/env python3
"""Check the actual staged release artifacts, including licensing and API documentation."""
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile

version = sys.argv[1]
root = Path(__file__).resolve().parent.parent
license_text = (root / 'LICENSE').read_bytes()
group = 'com.squareup.overstory'
for module in ('core', 'view-compat'):
    base = root / 'build/repository' / group.replace('.', '/') / module / version
    def artifact_path(extension, classifier=''):
        resolved_version = version
        if version.endswith('-SNAPSHOT'):
            metadata = ET.parse(base / 'maven-metadata.xml').getroot()
            matches = [entry.findtext('value')
                       for entry in metadata.findall('versioning/snapshotVersions/snapshotVersion')
                       if entry.findtext('extension') == extension
                       and entry.findtext('classifier', '') == classifier]
            assert len(matches) == 1 and matches[0], (module, extension, classifier)
            resolved_version = matches[0]
        suffix = f'-{classifier}' if classifier else ''
        path = base / f'{module}-{resolved_version}{suffix}.{extension}'
        assert path.parent == base, path
        return path

    for extension, classifier in (('aar', ''), ('jar', 'sources'), ('jar', 'javadoc')):
        artifact = artifact_path(extension, classifier)
        with ZipFile(artifact) as archive:
            assert archive.namelist().count('META-INF/LICENSE') == 1, artifact
            assert archive.read('META-INF/LICENSE') == license_text, artifact
            if classifier == 'sources':
                assert any(p.endswith('.kt') for p in archive.namelist()), artifact
            if classifier == 'javadoc':
                assert any(p.endswith('.html') for p in archive.namelist()), artifact
        print(f'Verified {artifact.name}')
    pom = ET.parse(artifact_path('pom')).getroot()
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    assert pom.findtext('m:groupId', namespaces=ns) == group, module
    assert pom.findtext('m:version', namespaces=ns) == version, module
    assert pom.findtext('m:url', namespaces=ns) == 'https://github.com/block/overstory', module
    metadata = json.loads(artifact_path('module').read_text())
    assert metadata['component']['group'] == group, module
    assert metadata['component']['version'] == version, module
    if module == 'view-compat':
        core = [d for d in pom.findall('m:dependencies/m:dependency', ns)
                if d.findtext('m:artifactId', namespaces=ns) == 'core']
        assert len(core) == 1, module
        assert core[0].findtext('m:groupId', namespaces=ns) == group, module
        assert core[0].findtext('m:version', namespaces=ns) == version, module
