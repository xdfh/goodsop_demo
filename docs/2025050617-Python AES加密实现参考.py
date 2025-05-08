#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Python AES加密实现参考
提供与Goodsop系统Java后端兼容的Python AES加密/解密实现
"""

from Crypto.Cipher import AES
import os
import binascii
import base64
import secrets

# AES加密相关常量 - 与Java端保持一致
AES_KEY = "1234567890abcdef1234567890abcdef"  # 32字节密钥
IV_SIZE = 16  # 初始向量(IV)大小，固定为16字节
ALGORITHM = AES.MODE_CTR  # CTR模式 - 与Java的AES/CTR/NoPadding对应


def encrypt_file(input_path, output_path, key=AES_KEY):
    """
    AES-CTR加密文件
    
    Args:
        input_path: 输入文件路径
        output_path: 输出文件路径
        key: AES密钥，默认使用常量AES_KEY
    
    Returns:
        bool: 加密是否成功
    """
    try:
        # 确保密钥长度正确(32字节)
        if len(key) != 32:
            print(f"警告: 密钥长度应为32字节, 当前长度: {len(key)}字节")
            return False
        
        # 生成随机16字节IV
        iv = secrets.token_bytes(IV_SIZE)
        
        # 创建AES加密器 - CTR模式
        # 在CTR模式中，nonce使用IV的前8字节，counter使用IV的后8字节作为初始值
        cipher = AES.new(key.encode('utf-8'), ALGORITHM, 
                         nonce=iv[:8], 
                         initial_value=int.from_bytes(iv[8:], byteorder='big'))
        
        # 读取文件内容
        with open(input_path, 'rb') as f_in:
            data = f_in.read()
        
        # 加密数据 (CTR模式不需要填充)
        encrypted_data = cipher.encrypt(data)
        
        # 将加密后的数据写入文件 - 先写入IV，再写入加密数据
        with open(output_path, 'wb') as f_out:
            f_out.write(iv)  # 前16字节是IV
            f_out.write(encrypted_data)
        
        print(f"文件加密成功: {input_path} -> {output_path}")
        print(f"IV长度: {len(iv)}字节, 加密数据长度: {len(encrypted_data)}字节")
        return True
    
    except Exception as e:
        print(f"加密文件失败: {str(e)}")
        return False


def decrypt_file(input_path, output_path, key=AES_KEY):
    """
    AES-CTR解密文件
    
    Args:
        input_path: 输入文件路径
        output_path: 输出文件路径
        key: AES密钥，默认使用常量AES_KEY
    
    Returns:
        bool: 解密是否成功
    """
    try:
        # 确保密钥长度正确(32字节)
        if len(key) != 32:
            print(f"警告: 密钥长度应为32字节, 当前长度: {len(key)}字节")
            return False
        
        with open(input_path, 'rb') as f_in:
            # 首先读取IV (前16字节)
            iv = f_in.read(IV_SIZE)
            if len(iv) != IV_SIZE:
                print(f"错误: 无法读取完整的IV，只读取到{len(iv)}字节")
                return False
            
            # 创建AES解密器 - CTR模式
            cipher = AES.new(key.encode('utf-8'), ALGORITHM, 
                            nonce=iv[:8], 
                            initial_value=int.from_bytes(iv[8:], byteorder='big'))
            
            # 读取剩余的加密数据
            encrypted_data = f_in.read()
            
            # 解密数据
            decrypted_data = cipher.decrypt(encrypted_data)
        
        # 将解密后的数据写入文件
        with open(output_path, 'wb') as f_out:
            f_out.write(decrypted_data)
        
        print(f"文件解密成功: {input_path} -> {output_path}")
        return True
    
    except Exception as e:
        print(f"解密文件失败: {str(e)}")
        return False


def encrypt_text(text, key=AES_KEY):
    """
    AES-CTR加密文本
    
    Args:
        text: 要加密的文本
        key: AES密钥，默认使用常量AES_KEY
    
    Returns:
        str: 格式为"iv:encrypted_data"的Base64编码字符串
    """
    try:
        # 生成随机IV
        iv = secrets.token_bytes(IV_SIZE)
        
        # 创建AES加密器
        cipher = AES.new(key.encode('utf-8'), ALGORITHM, 
                         nonce=iv[:8], 
                         initial_value=int.from_bytes(iv[8:], byteorder='big'))
        
        # 加密文本
        encrypted_data = cipher.encrypt(text.encode('utf-8'))
        
        # 将IV和加密数据组合并Base64编码
        combined = iv + encrypted_data
        return base64.b64encode(combined).decode('utf-8')
    
    except Exception as e:
        print(f"加密文本失败: {str(e)}")
        return None


def decrypt_text(encrypted_text, key=AES_KEY):
    """
    AES-CTR解密文本
    
    Args:
        encrypted_text: Base64编码的加密文本(包含IV+加密数据)
        key: AES密钥，默认使用常量AES_KEY
    
    Returns:
        str: 解密后的文本
    """
    try:
        # 解码Base64
        combined = base64.b64decode(encrypted_text)
        
        # 提取IV和加密数据
        iv = combined[:IV_SIZE]
        encrypted_data = combined[IV_SIZE:]
        
        # 创建AES解密器
        cipher = AES.new(key.encode('utf-8'), ALGORITHM, 
                         nonce=iv[:8], 
                         initial_value=int.from_bytes(iv[8:], byteorder='big'))
        
        # 解密并返回文本
        return cipher.decrypt(encrypted_data).decode('utf-8')
    
    except Exception as e:
        print(f"解密文本失败: {str(e)}")
        return None


def chunk_encrypt_file(input_path, output_path, chunk_size=1024*1024, key=AES_KEY):
    """
    分块加密大文件 - 适用于需要分块上传的场景
    
    Args:
        input_path: 输入文件路径
        output_path: 输出文件路径
        chunk_size: 分块大小(字节)，默认1MB
        key: AES密钥，默认使用常量AES_KEY
    
    Returns:
        bool: 加密是否成功
    """
    try:
        # 确保密钥长度正确
        if len(key) != 32:
            print(f"警告: 密钥长度应为32字节, 当前长度: {len(key)}字节")
            return False
        
        # 生成随机IV
        iv = secrets.token_bytes(IV_SIZE)
        
        # 创建AES加密器 - CTR模式可以处理任意长度数据，不需要填充
        cipher = AES.new(key.encode('utf-8'), ALGORITHM, 
                         nonce=iv[:8], 
                         initial_value=int.from_bytes(iv[8:], byteorder='big'))
        
        # 获取文件大小
        file_size = os.path.getsize(input_path)
        
        with open(input_path, 'rb') as f_in, open(output_path, 'wb') as f_out:
            # 首先写入IV
            f_out.write(iv)
            
            # 处理完整的块
            chunks_processed = 0
            bytes_read = 0
            
            while bytes_read < file_size:
                # 读取一个块的数据
                chunk = f_in.read(chunk_size)
                bytes_read += len(chunk)
                chunks_processed += 1
                
                # 加密并写入块
                encrypted_chunk = cipher.encrypt(chunk)
                f_out.write(encrypted_chunk)
                
                print(f"加密块 {chunks_processed}: {len(chunk)} 字节")
        
        print(f"文件分块加密完成: {input_path} -> {output_path}, 共 {chunks_processed} 块")
        return True
    
    except Exception as e:
        print(f"分块加密文件失败: {str(e)}")
        return False


def chunk_decrypt_file(input_path, output_path, chunk_size=1024*1024, key=AES_KEY):
    """
    分块解密大文件
    
    Args:
        input_path: 输入文件路径
        output_path: 输出文件路径
        chunk_size: 分块大小(字节)，默认1MB
        key: AES密钥，默认使用常量AES_KEY
    
    Returns:
        bool: 解密是否成功
    """
    try:
        # 确保密钥长度正确
        if len(key) != 32:
            print(f"警告: 密钥长度应为32字节, 当前长度: {len(key)}字节")
            return False
        
        with open(input_path, 'rb') as f_in:
            # 首先读取IV (前16字节)
            iv = f_in.read(IV_SIZE)
            if len(iv) != IV_SIZE:
                print(f"错误: 无法读取完整的IV，只读取到{len(iv)}字节")
                return False
            
            # 创建AES解密器
            cipher = AES.new(key.encode('utf-8'), ALGORITHM, 
                            nonce=iv[:8], 
                            initial_value=int.from_bytes(iv[8:], byteorder='big'))
            
            # 获取剩余文件大小
            remaining_size = os.path.getsize(input_path) - IV_SIZE
            
            with open(output_path, 'wb') as f_out:
                # 分块解密
                chunks_processed = 0
                bytes_read = 0
                
                while bytes_read < remaining_size:
                    # 读取加密块
                    read_size = min(chunk_size, remaining_size - bytes_read)
                    encrypted_chunk = f_in.read(read_size)
                    bytes_read += len(encrypted_chunk)
                    chunks_processed += 1
                    
                    # 解密块并写入
                    decrypted_chunk = cipher.decrypt(encrypted_chunk)
                    f_out.write(decrypted_chunk)
                    
                    print(f"解密块 {chunks_processed}: {len(encrypted_chunk)} 字节")
        
        print(f"文件分块解密完成: {input_path} -> {output_path}, 共 {chunks_processed} 块")
        return True
    
    except Exception as e:
        print(f"分块解密文件失败: {str(e)}")
        return False


if __name__ == "__main__":
    # 使用示例
    print("Python AES-CTR加密示例")
    print(f"使用密钥: {AES_KEY}")
    print(f"密钥长度: {len(AES_KEY)} 字节")
    
    # 测试文本加密/解密
    original_text = "这是一段测试文本，用于验证AES-CTR加密/解密功能"
    print(f"\n原始文本: {original_text}")
    
    encrypted = encrypt_text(original_text)
    print(f"加密后(Base64): {encrypted}")
    
    decrypted = decrypt_text(encrypted)
    print(f"解密后: {decrypted}")
    print(f"解密验证: {'成功' if original_text == decrypted else '失败'}")
    
    # 测试说明
    print("\n要加密/解密文件，请使用以下函数:")
    print("encrypt_file('input.txt', 'input.txt.enc')")
    print("decrypt_file('input.txt.enc', 'input_decrypted.txt')")
    print("chunk_encrypt_file('large_file.dat', 'large_file.dat.enc')")
    print("chunk_decrypt_file('large_file.dat.enc', 'large_file_decrypted.dat')")
    
    print("\n注意：")
    print("1. 加密文件的前16字节是IV，后续字节是加密数据")
    print("2. CTR模式不需要填充，可以加密任意长度的数据")
    print("3. 请确保密钥长度为32字节(AES-256)") 