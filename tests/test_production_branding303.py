import importlib.util
import unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('branding',Path(__file__).parents[1]/'scripts/verify-production-branding303.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class ProductionBrandingTests(unittest.TestCase):
 def fixture(self,package='app.radvpn',version='3.0.3',label='RADVPN'):
  return f"package: name='{package}' versionCode='30003' versionName='{version}'\napplication-label:'{label}'\n"
 def test_original_identity_and_normal_name(self):
  self.assertEqual(m.verify_badging(self.fixture())['label'],'RADVPN')
 def test_renamed_testing_apk_cannot_pass(self):
  with self.assertRaises(ValueError):m.verify_badging(self.fixture(package='app.radvpn.testing'))
 def test_test_display_name_cannot_pass(self):
  with self.assertRaises(ValueError):m.verify_badging(self.fixture(label='RADVPN Test 3.0.3'))
 def test_testing_version_cannot_pass(self):
  with self.assertRaises(ValueError):m.verify_badging(self.fixture(version='3.0.3-testing'))
 def test_every_localized_label_must_be_normal(self):
  with self.assertRaises(ValueError):m.verify_badging(self.fixture()+"application-label-fa:'RADVPN Test'\n")
 def test_debug_variant_cannot_pass(self):
  with self.assertRaises(ValueError):m.verify_badging(self.fixture()+'application-debuggable\n')

if __name__=='__main__':unittest.main()
