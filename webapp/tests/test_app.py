import os, sys, re
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
os.environ["SECRET_KEY"] = "test-secret-key-that-is-long-enough-for-tests"
os.environ["DATABASE_URL"] = "sqlite:////tmp/gce_notice_board_test.db"
from fastapi.testclient import TestClient
import main
from database import Base, engine

Base.metadata.drop_all(bind=engine)
Base.metadata.create_all(bind=engine)
main.seed_data()
client = TestClient(main.app)

def login(login_id, password):
    with client:
        r = client.get('/login')
        csrf = re.search(r'name="csrf_token" value="([^"]+)"', r.text).group(1)
        return client.post('/login', data={'login_id':login_id,'password':password,'csrf_token':csrf}, follow_redirects=False)

def test_home_and_health():
    assert client.get('/health').json()['status'] == 'ok'
    r = client.get('/')
    assert r.status_code == 200
    assert 'Digital Notice Board' in r.text
    assert 'Welcome to the GCE Salem Digital Notice Board' in r.text

def test_login_and_api_token():
    r = client.post('/api/auth/login', json={'login_id':'admin','password':'admin123'})
    assert r.status_code == 200
    token = r.json()['access_token']
    assert client.get('/api/auth/me', headers={'Authorization':f'Bearer {token}'}).status_code == 200

def test_student_cannot_publish():
    r = client.post('/api/auth/login', json={'login_id':'student_cse','password':'student123'})
    token = r.json()['access_token']
    r = client.post('/api/notices', headers={'Authorization':f'Bearer {token}'}, json={'title':'Should fail','description':'Student cannot publish','department':'CSE'})
    assert r.status_code == 403

def test_faculty_cannot_publish_to_other_department():
    r = client.post('/api/auth/login', json={'login_id':'faculty_cse','password':'faculty123'})
    token = r.json()['access_token']
    r = client.post('/api/notices', headers={'Authorization':f'Bearer {token}'}, json={'title':'Should fail','description':'Wrong department','department':'IT'})
    assert r.status_code == 403

def test_admin_can_create_notice():
    r = client.post('/api/auth/login', json={'login_id':'admin','password':'admin123'})
    token = r.json()['access_token']
    r = client.post('/api/notices', headers={'Authorization':f'Bearer {token}'}, json={'title':'Automated test notice','description':'API test','department':'CSE','category':'General','priority':'MEDIUM'})
    assert r.status_code == 200
    assert r.json()['title'] == 'Automated test notice'

def test_upload_requires_login():
    r = client.post('/api/notices/1/attachments', files=[('files',('note.pdf',b'%PDF-1.4 test','application/pdf'))])
    assert r.status_code == 401

def test_real_file_upload_and_download():
    auth = client.post('/api/auth/login', json={'login_id':'admin','password':'admin123'}).json()
    headers = {'Authorization': f"Bearer {auth['access_token']}"}
    notice = client.post('/api/notices', headers=headers, json={'title':'Upload test notice','description':'A notice with a real PDF attachment','department':'CSE','category':'Circular','priority':'MEDIUM'}).json()
    uploaded = client.post(f"/api/notices/{notice['id']}/attachments", headers=headers, files=[('files', ('sample.pdf', b'%PDF-1.4\nactual test bytes', 'application/pdf'))])
    assert uploaded.status_code == 200, uploaded.text
    attachment = uploaded.json()['uploaded'][0]
    downloaded = client.get(attachment['url'], headers=headers)
    assert downloaded.status_code == 200
    assert downloaded.content == b'%PDF-1.4\nactual test bytes'
    assert 'sample.pdf' in downloaded.headers.get('content-disposition', '')
