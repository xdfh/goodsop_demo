# Python客户端AES加密实现指南

## 问题背景

目前Goodsop系统在文件上传过程中发现有解密错误，错误为`javax.crypto.BadPaddingException: Given final block not properly padded`。该错误通常是由于客户端加密时与服务端解密方式不匹配导致的。我们已将加密模式从ECB改为CTR模式，现更新客户端加密标准。

## AES加密规范

### 基本要求

1. **加密算法**: AES-256
2. **加密模式**: CTR (计数器模式)
3. **填充方式**: NoPadding (CTR模式不需要填充)
4. **字符编码**: UTF-8 (密钥编码)
5. **密钥长度**: 32字节 (256位)
6. **初始向量(IV)**: 16字节随机生成值

### 标准密钥参考值
```
1234567890abcdef1234567890abcdef
```

**注意**: 
- 密钥必须使用UTF-8编码转换为字节数组
- IV必须随机生成，且长度为16字节
- 加密文件时，IV必须存储在加密文件的开头

### 文件命名规范

- 加密文件统一使用`.enc`后缀
- 如文件已压缩再加密，使用`.gz.enc`或`.zip.enc`后缀

## Python实现示例

### 必要依赖
```bash
pip install pycryptodome
```

### 标准实现代码

```python
from Crypto.Cipher import AES
from Crypto.Util import Counter
import os
import secrets

# 与Java端一致的加密常量
AES_KEY = "1234567890abcdef1234567890abcdef"  # 32字节密钥
IV_SIZE = 16  # 初始向量大小(字节)

def encrypt_file(input_path, output_path, key=AES_KEY):
    """AES-CTR加密文件"""
    try:
        # 确保密钥长度为32字节
        if len(key) != 32:
            raise ValueError(f"密钥长度错误: 应为32字节, 当前为{len(key)}字节")
        
        # 生成随机16字节IV
        iv = secrets.token_bytes(IV_SIZE)
        
        # 创建AES加密器 - CTR模式
        cipher = AES.new(key.encode('utf-8'), AES.MODE_CTR, nonce=iv[:8], initial_value=int.from_bytes(iv[8:], byteorder='big'))
        
        # 读取文件内容
        with open(input_path, 'rb') as f_in:
            data = f_in.read()
        
        # 加密数据
        encrypted_data = cipher.encrypt(data)
        
        # 写入加密文件 - 先写IV，再写加密数据
        with open(output_path, 'wb') as f_out:
            f_out.write(iv)
            f_out.write(encrypted_data)
        
        print(f"文件加密成功: {input_path} -> {output_path}")
        return True
    
    except Exception as e:
        print(f"加密失败: {e}")
        return False

def decrypt_file(input_path, output_path, key=AES_KEY):
    """AES-CTR解密文件"""
    try:
        # 确保密钥长度为32字节
        if len(key) != 32:
            raise ValueError(f"密钥长度错误: 应为32字节, 当前为{len(key)}字节")
        
        with open(input_path, 'rb') as f_in:
            # 读取前16字节作为IV
            iv = f_in.read(IV_SIZE)
            if len(iv) != IV_SIZE:
                raise ValueError("无法读取完整IV，文件可能已损坏")
                
            # 创建AES解密器
            cipher = AES.new(key.encode('utf-8'), AES.MODE_CTR, nonce=iv[:8], initial_value=int.from_bytes(iv[8:], byteorder='big'))
            
            # 读取剩余加密数据
            encrypted_data = f_in.read()
            
            # 解密数据
            decrypted_data = cipher.decrypt(encrypted_data)
            
            # 写入解密后的数据
            with open(output_path, 'wb') as f_out:
                f_out.write(decrypted_data)
        
        print(f"文件解密成功: {input_path} -> {output_path}")
        return True
    
    except Exception as e:
        print(f"解密失败: {e}")
        return False
```

## 分块加密大文件

对于大型文件，需要分块处理：

```python
def chunk_encrypt_file(input_path, output_path, chunk_size=1024*1024, key=AES_KEY):
    """分块加密大文件"""
    try:
        # 生成随机16字节IV
        iv = secrets.token_bytes(IV_SIZE)
        
        # 创建AES加密器
        cipher = AES.new(key.encode('utf-8'), AES.MODE_CTR, nonce=iv[:8], initial_value=int.from_bytes(iv[8:], byteorder='big'))
        
        # 获取文件大小
        file_size = os.path.getsize(input_path)
        
        with open(input_path, 'rb') as f_in, open(output_path, 'wb') as f_out:
            # 首先写入IV
            f_out.write(iv)
            
            chunks_processed = 0
            
            while True:
                # 读取一个块
                chunk = f_in.read(chunk_size)
                if not chunk:
                    break
                
                chunks_processed += 1
                
                # 加密并写入
                encrypted_chunk = cipher.encrypt(chunk)
                f_out.write(encrypted_chunk)
        
        print(f"分块加密完成: {input_path} -> {output_path}, 共{chunks_processed}块")
        return True
    
    except Exception as e:
        print(f"分块加密失败: {e}")
        return False
```

## 常见错误与解决方案

### 1. IV处理不当

**问题**: 服务端解密时抛出解密错误

**可能原因**:
- IV未正确保存在加密文件的开头
- IV长度不正确（必须为16字节）
- IV格式或使用方式与服务端不匹配

**解决方案**:
- 确保IV长度为16字节
- 确保将IV写入加密文件的开头
- 确保解密时先读取IV再进行解密

### 2. 加密模式不匹配

**问题**: 解密失败并出现数据不完整

**解决方案**:
- 确保使用CTR模式而非ECB模式
- 确保使用NoPadding（CTR模式不需要填充）
- 确保CTR模式的nonce和initial_value正确配置

### 3. 密钥长度不匹配

**问题**: "Invalid AES key length"

**解决方案**:
- 确保密钥长度为32字节(256位)
- 不要对密钥进行哈希或其他转换
- 直接使用原始字符串的UTF-8编码

## 测试确认

实现完成后，请使用以下步骤测试加密是否正确:

1. 加密一个小文本文件
2. 将加密后的文件上传到Goodsop系统
3. 检查服务端日志，确认解密成功

如有问题，请开发团队联系服务端开发人员进行协调。 